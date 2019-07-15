package com.r3.demo.tokens.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.utilities.withNotary
import com.r3.corda.lib.tokens.workflows.flows.rpc.CreateEvolvableTokens
import com.r3.demo.tokens.state.DiamondGradingReport
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction

/**
 * Implements the create evolvable token flow.
 */
@InitiatingFlow
@StartableByRPC
class CreateDiamondGradingReportFlow(private val diamondGradingReport: DiamondGradingReport) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val transactionState = diamondGradingReport withNotary notary

        return subFlow(CreateEvolvableTokens(transactionState))
    }
}

