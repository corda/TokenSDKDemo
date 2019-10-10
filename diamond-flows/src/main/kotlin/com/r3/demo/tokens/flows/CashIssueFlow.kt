package com.r3.demo.tokens.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount
import com.r3.corda.lib.accounts.workflows.internal.flows.createKeyForAccount

import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.heldBy
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.workflows.flows.issue.IssueTokensFlow
import com.r3.corda.lib.tokens.workflows.flows.issue.IssueTokensFlowHandler
import net.corda.core.contracts.Amount
import net.corda.core.flows.*
import net.corda.core.identity.AnonymousParty
import net.corda.core.transactions.SignedTransaction

@InitiatingFlow
@StartableByRPC
/**
 * Self issues the amount of cash in the desired currency and pays the receiver
 */
class CashIssueFlow(private val accountInfo: AccountInfo, private val amount: Amount<TokenType>) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        // Generate key for transaction, if receiver not on this
        // node then use sub-flow to request for key
        val owningKey =
            if (accountInfo.host == ourIdentity) {
                createKeyForAccount(accountInfo, serviceHub).owningKey
            } else {
                subFlow(RequestKeyForAccount(accountInfo)).owningKey
            }

        val token = amount issuedBy ourIdentity heldBy AnonymousParty(owningKey)
        val flows = listOf(initiateFlow(accountInfo.host))
        val flow = IssueTokensFlow(token, flows, emptyList())

        return subFlow(flow)
    }

    @Suppress("unused")
    @InitiatedBy(CashIssueFlow::class)
    class CashIssueFlowResponse (private val flowSession: FlowSession): FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            subFlow(IssueTokensFlowHandler(flowSession))
        }
    }
}

