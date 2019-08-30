package com.r3.demo.tokens.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.heldBy
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.workflows.flows.issue.IssueTokensFlow
import com.r3.corda.lib.tokens.workflows.flows.issue.IssueTokensFlowHandler
import com.r3.corda.lib.tokens.workflows.flows.redeem.addFungibleTokensToRedeem
import net.corda.core.contracts.Amount
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap

@InitiatingFlow
@StartableByRPC
/**
 * Reissues the amount of cash in the desired currency and pays the receiver
 */
class CashReissueFlow(val issuer: Party, val amount: Amount<TokenType>) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val token = amount issuedBy issuer heldBy ourIdentity

        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val builder = TransactionBuilder(notary)

        // Create transaction to redeem the cash
        addFungibleTokensToRedeem(builder, serviceHub, amount, issuer, ourIdentity, null)

        val redeemFlows = initiateFlow(issuer)

        // Self sign the transaction
        val selfSignedTransaction = serviceHub.signInitialTransaction(builder)

        // Get issuer to sign the transaction
        val fullySignedTransaction = subFlow(CollectSignaturesFlow(selfSignedTransaction, listOf(redeemFlows)))

        // Notify the notary
        subFlow(FinalityFlow(fullySignedTransaction, redeemFlows))

        // Send details of issue token to issuer
        redeemFlows.send(Info(fullySignedTransaction, token))

        // Handle the issue token transaction
        subFlow(IssueTokensFlowHandler(redeemFlows))

        // Receive issue token transaction from issuer
        val txInfo = redeemFlows.receive<Info>().unwrap { it }

        // Return transaction id
        return txInfo.txId
    }

    @Suppress("unused")
    @InitiatedBy(CashReissueFlow::class)
    class CashReissueFlowResponse (val flowSession: FlowSession): FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val signedTransactionFlow = object : SignTransactionFlow(flowSession, SignTransactionFlow.tracker()){
                override fun checkTransaction(stx: SignedTransaction) {
                    // Perform validation
                }
            }

            // Issuer signs the redemption transaction
            val signedTransaction = subFlow(signedTransactionFlow)

            // Issuer handles the finality flow
            subFlow(ReceiveFinalityFlow(flowSession, expectedTxId = signedTransaction.id))

            // Receive the reissue details from user
            val txInfo = flowSession.receive<Info>().unwrap { it }

            // Reissue the fungible token
            val flow = IssueTokensFlow(txInfo.token, listOf(flowSession), emptyList())

            val issueTransaction = subFlow(flow)

            // Send back issue token transaction to user
            flowSession.send(Info(issueTransaction, txInfo.token))
        }
    }

    @CordaSerializable
    data class Info(
            val txId: SignedTransaction,
            val token: FungibleToken
    )
}

