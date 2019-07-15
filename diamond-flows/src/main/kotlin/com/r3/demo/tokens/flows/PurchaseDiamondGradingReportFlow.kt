package com.r3.demo.tokens.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.workflows.flows.issue.IssueTokensFlowHandler
import com.r3.corda.lib.tokens.workflows.flows.issue.addIssueTokens
import com.r3.corda.lib.tokens.workflows.internal.flows.distribution.UpdateDistributionListFlow
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.ObserverAwareFinalityFlow
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveFungibleTokens
import com.r3.corda.lib.tokens.workflows.utilities.heldBy
import com.r3.demo.tokens.state.DiamondGradingReport
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap

/**
 * Implements the purchase token flow.
 * The buyer wants buy a token from an issuer and provides the payment.
 * The flow is initiated by the issuer.
 * The buyer creates the transaction with payment which is then verified by the issuer.
 */
@InitiatingFlow
@StartableByRPC
class PurchaseDiamondGradingReportFlow(
        private val reportId: UniqueIdentifier,
        private val buyer: Party,
        private val amount: Amount<TokenType>) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val diamondGradingReportRef = getStateReference(serviceHub, DiamondGradingReport::class.java, reportId)
        val diamondGradingReport = diamondGradingReportRef.state.data
        val diamondPointer = diamondGradingReport.toPointer<DiamondGradingReport>()
        val token = diamondPointer issuedBy ourIdentity heldBy buyer

        val other = initiateFlow(buyer)

        // Send the full TokenPointer details to the buyer, this must be
        // available in the buyer's vault
        val tx = serviceHub.validatedTransactions.getTransaction(diamondGradingReportRef.ref.txhash)!!
        subFlow(SendTransactionFlow(other, tx))

        // Send trade details
        other.send(SellerTradeInfo(amount, token, ourIdentity))

        val signedTransactionFlow = object : SignTransactionFlow(other, SignTransactionFlow.tracker()){
            override fun checkTransaction(stx: SignedTransaction) {
            }
        }
        val txid = subFlow(signedTransactionFlow)

        subFlow(IssueTokensFlowHandler(other))

        return txid
    }

    @Suppress("unused")
    @InitiatedBy(PurchaseDiamondGradingReportFlow::class)
    class PurchaseDiamondGradingReportFlowResponse (private val flowSession: FlowSession): FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            // Receive and record the TokenPointer
            subFlow(ReceiveTransactionFlow(flowSession, statesToRecord = StatesToRecord.ALL_VISIBLE))

            // Receive the trade details
            val tradeInfo = flowSession.receive<SellerTradeInfo>().unwrap { it }

            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            val builder = TransactionBuilder(notary)

            // Issue the token and exchange payment
            addIssueTokens(builder, listOf(tradeInfo.token))
            addMoveFungibleTokens(builder, serviceHub, tradeInfo.price, tradeInfo.party, ourIdentity, null)

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
            val price: Amount<TokenType>,
            val token: NonFungibleToken,
            val party: Party
    )
}


