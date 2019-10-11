package com.r3.demo.tokens.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.workflows.flows.rpc.UpdateEvolvableToken
import com.r3.demo.tokens.state.DiamondGradingReport
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction

/**
 * Implements the update evolvable token flow.
 */
@InitiatingFlow
@StartableByRPC
class UpdateDiamondGradingReportFlow(private val reportId: UniqueIdentifier, private val updatedGradingReport: DiamondGradingReport) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val diamondGradingReportRef = getStateReference(serviceHub, DiamondGradingReport::class.java, reportId)

        val flows = updatedGradingReport.participants
                .filter{ party -> party != ourIdentity }
                .map{ party -> initiateFlow(party as Party) }

        val flow = UpdateEvolvableToken(diamondGradingReportRef, updatedGradingReport)
        val signedTransaction = subFlow(flow)

        return subFlow(FinalityFlow(signedTransaction, flows))
    }

    @Suppress("unused")
    @InitiatedBy(UpdateDiamondGradingReportFlow::class)
    class UpdateDiamondGradingReportFlowResponse (private val flowSession: FlowSession): FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signedTransactionFlow = object : SignTransactionFlow(flowSession, tracker()){
                override fun checkTransaction(stx: SignedTransaction) {
                }
            }
            val txId = subFlow(signedTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(flowSession, expectedTxId = txId))
        }
    }
}
