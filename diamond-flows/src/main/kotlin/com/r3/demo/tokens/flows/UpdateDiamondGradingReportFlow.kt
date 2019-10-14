package com.r3.demo.tokens.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.workflows.flows.rpc.UpdateEvolvableToken
import com.r3.demo.tokens.state.DiamondGradingReport
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
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

        return subFlow(UpdateEvolvableToken(diamondGradingReportRef, updatedGradingReport))
    }
}
