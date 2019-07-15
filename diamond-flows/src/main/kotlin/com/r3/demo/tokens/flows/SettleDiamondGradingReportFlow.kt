package com.r3.demo.tokens.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.workflows.flows.redeem.RedeemTokensFlow
import com.r3.corda.lib.tokens.workflows.flows.redeem.RedeemTokensFlowHandler
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction

/**
 * Implements the redeem token flow without payment.
 */
@InitiatingFlow
@StartableByRPC
class SettleDiamondGradingReportFlow(private val tokenId: UniqueIdentifier, private val issuer: Party) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val original = getStateReference(serviceHub, NonFungibleToken::class.java, tokenId)
        val flow = RedeemTokensFlow(listOf(original), null, initiateFlow(issuer))

        return subFlow(flow)
    }

    @Suppress("unused")
    @InitiatedBy(SettleDiamondGradingReportFlow::class)
    class SettleDiamondGradingReportFlowResponse (private val flowSession: FlowSession): FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            subFlow(RedeemTokensFlowHandler(flowSession))
        }
    }
}
