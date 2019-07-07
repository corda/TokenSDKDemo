package com.r3.demo.tokens.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.workflows.flows.issue.IssueTokensFlowHandler
import com.r3.corda.lib.tokens.workflows.flows.issue.addIssueTokens
import com.r3.corda.lib.tokens.workflows.internal.flows.distribution.UpdateDistributionListFlow
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.ObserverAwareFinalityFlow
import com.r3.corda.lib.tokens.workflows.utilities.heldBy
import com.r3.demo.tokens.state.DiamondGradingReport
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

/**
 * Implements the issue token flow without payment.
 */
@InitiatingFlow
@StartableByRPC
class IssueDiamondGradingReportFlow(
        private val reportId: UniqueIdentifier,
        private val buyer: Party) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val diamondGradingReportRef = getStateReference(serviceHub, DiamondGradingReport::class.java, reportId)
        val diamondGradingReport = diamondGradingReportRef.state.data
        val diamondPointer = diamondGradingReport.toPointer<DiamondGradingReport>()
        val token = diamondPointer issuedBy ourIdentity heldBy buyer

        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val builder = TransactionBuilder(notary)

        addIssueTokens(builder, listOf( token ))

        val others = listOf( ourIdentity ).map{ party -> initiateFlow(party) }
        val flows = listOf( ourIdentity, buyer ).map{ party -> initiateFlow(party) }

        val selfSignedTransaction = serviceHub.signInitialTransaction(builder)
        val fullySignedTransaction = subFlow(CollectSignaturesFlow(selfSignedTransaction, others))
        val signedTransaction = subFlow(ObserverAwareFinalityFlow(fullySignedTransaction, flows))

        subFlow(UpdateDistributionListFlow(signedTransaction))

        return signedTransaction
    }

    @Suppress("unused")
    @InitiatedBy(IssueDiamondGradingReportFlow::class)
    class IssueDiamondGradingReportFlowResponse (private val flowSession: FlowSession): FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            subFlow(IssueTokensFlowHandler(flowSession))
        }
    }
}


