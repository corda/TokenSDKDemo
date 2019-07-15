package com.r3.demo.tokens.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.ObserverAwareFinalityFlowHandler
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveFungibleTokens
import com.r3.corda.lib.tokens.workflows.flows.redeem.addTokensToRedeem
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.ObserverAwareFinalityFlow
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap

/**
 * Implements the redeem token flow.
 * The holder of the token wants to redeem the token and receive the payment.
 * The flow is initiated by the holder.
 * The issuer creates the transaction with payment which is then verified by the holder.
 */
@InitiatingFlow
@StartableByRPC
class RedeemDiamondGradingReportFlow(
        private val tokenId: UniqueIdentifier,
        private val issuer: Party,
        private val amount: Amount<TokenType>) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val original = getStateReference(serviceHub, NonFungibleToken::class.java, tokenId)
        val other = initiateFlow(issuer)

        // Send the state reference for the token being redeemed
        subFlow(SendStateAndRefFlow(other, listOf(original)))

        // Send trade details
        other.send(SellerTradeInfo(amount, ourIdentity))

        val signedTransactionFlow = object : SignTransactionFlow(other, SignTransactionFlow.tracker()){
            override fun checkTransaction(stx: SignedTransaction) {

            }
        }

        val txid = subFlow(signedTransactionFlow)

        subFlow(ObserverAwareFinalityFlowHandler(other))

        return txid
    }

    @Suppress("unused")
    @InitiatedBy(RedeemDiamondGradingReportFlow::class)
    class RedeemDiamondGradingReportFlowResponse (private val flowSession: FlowSession): FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            // Receive the token being redeemed
            val originalToken = subFlow(ReceiveStateAndRefFlow<NonFungibleToken>(flowSession)).single()

            // Receive the trade details
            val tradeInfo = flowSession.receive<SellerTradeInfo>().unwrap { it }

            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            val builder = TransactionBuilder(notary)

            // Redeem the token and exchange payment
            addTokensToRedeem(builder, listOf(originalToken), null)
            addMoveFungibleTokens(builder, serviceHub, tradeInfo.price, tradeInfo.party, ourIdentity, null)

            // Sign off the transaction
            val selfSignedTransaction = serviceHub.signInitialTransaction(builder)
            val fullySignedTransaction = subFlow(CollectSignaturesFlow(selfSignedTransaction, listOf(flowSession)))

            // Notify the notary
            subFlow(ObserverAwareFinalityFlow(fullySignedTransaction, listOf(flowSession)))
        }
    }

    @CordaSerializable
    data class SellerTradeInfo(
            val price: Amount<TokenType>,
            val party: Party
    )
}
