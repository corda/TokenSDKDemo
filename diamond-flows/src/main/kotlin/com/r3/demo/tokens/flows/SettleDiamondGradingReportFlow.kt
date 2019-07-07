package com.r3.demo.tokens.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenPointer
import com.r3.corda.lib.tokens.workflows.flows.redeem.RedeemTokensFlow
import com.r3.corda.lib.tokens.workflows.flows.redeem.RedeemTokensFlowHandler
import com.r3.demo.tokens.state.DiamondGradingReport
import net.corda.core.contracts.StateAndRef
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
        @Suppress("unchecked_cast")
        val original = getStateReference(serviceHub, NonFungibleToken::class.java, tokenId)
                as StateAndRef<NonFungibleToken<TokenPointer<DiamondGradingReport>>>

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
