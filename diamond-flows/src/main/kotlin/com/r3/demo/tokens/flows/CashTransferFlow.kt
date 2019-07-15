package com.r3.demo.tokens.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.workflows.flows.move.MoveTokensFlowHandler
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.ObserverAwareFinalityFlow
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveFungibleTokens
import net.corda.core.contracts.Amount
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

/**
 * Implements the pay cash flow.
 */
@InitiatingFlow
@StartableByRPC
class CashTransferFlow(
        private val buyer: Party,
        private val amount: Amount<TokenType>) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val builder = TransactionBuilder(notary)

        addMoveFungibleTokens(builder, serviceHub, amount, buyer, ourIdentity, null)

        val others = listOf( ourIdentity ).map{ party -> initiateFlow(party) }
        val flows = listOf( ourIdentity, buyer ).map{ party -> initiateFlow(party) }

        val selfSignedTransaction = serviceHub.signInitialTransaction(builder)
        val fullySignedTransaction = subFlow(CollectSignaturesFlow(selfSignedTransaction, others))

        return subFlow(ObserverAwareFinalityFlow(fullySignedTransaction, flows))
    }

    @Suppress("unused")
    @InitiatedBy(CashTransferFlow::class)
    class CashTransferFlowResponse (private val flowSession: FlowSession): FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            subFlow(MoveTokensFlowHandler(flowSession))
        }
    }
}


