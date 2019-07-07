package com.r3.demo.tokens.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenPointer
import com.r3.corda.lib.tokens.workflows.flows.move.MoveTokensFlowHandler
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveTokens
import com.r3.corda.lib.tokens.workflows.internal.flows.distribution.UpdateDistributionListFlow
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.ObserverAwareFinalityFlow
import com.r3.corda.lib.tokens.money.FiatCurrency
import com.r3.demo.tokens.state.DiamondGradingReport
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap

/**
 * Implements the transfer token flow.
 * The buyer wants buy a token from the seller and provides the payment.
 * The flow is initiated by the seller.
 * The buyer creates the transaction with payment which is then verified by the seller.
 */
@InitiatingFlow
@StartableByRPC
class TransferDiamondGradingReportFlow(
        private val tokenId: UniqueIdentifier,
        private val buyer: Party,
        private val amount: Amount<FiatCurrency>) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        @Suppress("unchecked_cast")
        val original = getStateReference(serviceHub, NonFungibleToken::class.java, tokenId)
                as StateAndRef<NonFungibleToken<TokenPointer<DiamondGradingReport>>>

        val reportId = original.state.data.token.tokenType.pointer.pointer

        val diamondGradingReportRef = getStateReference(serviceHub, DiamondGradingReport::class.java, reportId)

        val other = initiateFlow(buyer)

        val tx = serviceHub.validatedTransactions.getTransaction(diamondGradingReportRef.ref.txhash)!!

        // Send tx containing the diamond grade report to buyer
        subFlow(SendTransactionFlow(other, tx))

        // Send original state and reference
        subFlow(SendStateAndRefFlow(other, listOf(original)))

        // Send trade details
        other.send(SellerTradeInfo(amount, ourIdentity))

        // Verify the transaction from the buyer is valid
        val signedTransactionFlow = object : SignTransactionFlow(other, SignTransactionFlow.tracker()){
            override fun checkTransaction(stx: SignedTransaction) {

            }
        }

        val txid = subFlow(signedTransactionFlow)

        subFlow(MoveTokensFlowHandler(other))

        return txid
    }

    @Suppress("unused")
    @InitiatedBy(TransferDiamondGradingReportFlow::class)
    class TransferDiamondGradingReportFlowResponse (private val flowSession: FlowSession): FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            // Receive and record the TokenPointer
            subFlow(ReceiveTransactionFlow(flowSession, statesToRecord = StatesToRecord.ALL_VISIBLE))

            // Receive the token being sold
            val originalToken = subFlow(ReceiveStateAndRefFlow<NonFungibleToken<TokenPointer<DiamondGradingReport>>>(flowSession)).single()

            // Receive the trade details
            val tradeInfo = flowSession.receive<SellerTradeInfo>().unwrap { it }

            // Update the token to the new owner
            val modifiedToken = NonFungibleToken(
                    token = originalToken.state.data.token,
                    linearId = originalToken.state.data.linearId,
                    holder = ourIdentity)

            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            val builder = TransactionBuilder(notary)

            // Exchange the token and payment
            addMoveTokens(builder, listOf(originalToken), listOf(modifiedToken))
            addMoveTokens(builder, serviceHub, tradeInfo.price, tradeInfo.party, ourIdentity, null)

            // Sign off the transaction
            val selfSignedTransaction = serviceHub.signInitialTransaction(builder)
            val fullySignedTransaction = subFlow(CollectSignaturesFlow(selfSignedTransaction, listOf(flowSession)))

            // Notify the notary
            val finalityTransaction = subFlow(ObserverAwareFinalityFlow(fullySignedTransaction, listOf(flowSession)))
            subFlow(UpdateDistributionListFlow(finalityTransaction))
        }
    }

    @CordaSerializable
    data class SellerTradeInfo(
            val price: Amount<FiatCurrency>,
            val party: Party
    )
}


