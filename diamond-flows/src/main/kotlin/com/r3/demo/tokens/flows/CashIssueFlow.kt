package com.r3.demo.tokens.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.utilities.heldBy
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.workflows.flows.issue.IssueTokensFlow
import com.r3.corda.lib.tokens.workflows.flows.issue.IssueTokensFlowHandler
import com.r3.corda.lib.tokens.money.FiatCurrency
import net.corda.core.contracts.Amount
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction

@InitiatingFlow
@StartableByRPC
/**
 * Self issues the amount of cash in the desired currency and pays the receiver
 */
class CashIssueFlow(val receiver: Party, val amount: Amount<FiatCurrency>) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val token = amount issuedBy ourIdentity heldBy receiver

        val flows = token.participants.map{ party -> initiateFlow(party as Party) }
        val flow = IssueTokensFlow(token, flows, emptyList())

        return subFlow(flow)
    }

    @Suppress("unused")
    @InitiatedBy(CashIssueFlow::class)
    class CashIssueFlowResponse (val flowSession: FlowSession): FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            subFlow(IssueTokensFlowHandler(flowSession))
        }
    }
}

