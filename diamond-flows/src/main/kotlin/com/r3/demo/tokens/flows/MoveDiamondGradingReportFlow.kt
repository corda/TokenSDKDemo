package com.r3.demo.tokens.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenPointer
import com.r3.corda.lib.tokens.workflows.flows.move.MoveTokensFlowHandler
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveTokens
import com.r3.corda.lib.tokens.workflows.internal.flows.distribution.UpdateDistributionListFlow
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.ObserverAwareFinalityFlow
import com.r3.demo.tokens.state.DiamondGradingReport
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

/**
 * Implements the move token flow without payment.
 */
@InitiatingFlow
@StartableByRPC
class MoveDiamondGradingReportFlow(private val tokenId: UniqueIdentifier, private val buyer: Party) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        @Suppress("unchecked_cast")
        val original = getStateReference(serviceHub, NonFungibleToken::class.java, tokenId)
                as StateAndRef<NonFungibleToken<TokenPointer<DiamondGradingReport>>>

        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val builder = TransactionBuilder(notary)

        val diamondPointer = original.state.data.token.tokenType
        val query = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(tokenId))

        addMoveTokens(builder, serviceHub, diamondPointer, buyer, query)

        val others = listOf( ourIdentity ).map{ party -> initiateFlow(party) }
        val flows = listOf( ourIdentity, buyer ).map{ party -> initiateFlow(party) }

        val selfSignedTransaction = serviceHub.signInitialTransaction(builder)
        val fullySignedTransaction = subFlow(CollectSignaturesFlow(selfSignedTransaction, others))
        val signedTransaction = subFlow(ObserverAwareFinalityFlow(fullySignedTransaction, flows))

        subFlow(UpdateDistributionListFlow(signedTransaction))

        return signedTransaction
    }

    @Suppress("unused")
    @InitiatedBy(MoveDiamondGradingReportFlow::class)
    class MoveDiamondGradingReportFlowResponse (private val flowSession: FlowSession): FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            subFlow(MoveTokensFlowHandler(flowSession))
        }
    }
}
