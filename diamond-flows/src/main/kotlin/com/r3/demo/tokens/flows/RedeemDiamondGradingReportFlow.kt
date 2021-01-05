package com.r3.demo.tokens.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount
import com.r3.corda.lib.accounts.workflows.flows.ShareAccountInfo
import com.r3.corda.lib.accounts.workflows.internal.flows.createKeyForAccount
import com.r3.corda.lib.ci.workflows.SyncKeyMappingInitiator
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.ObserverAwareFinalityFlowHandler
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveFungibleTokens
import com.r3.corda.lib.tokens.workflows.flows.redeem.addTokensToRedeem
import com.r3.corda.lib.tokens.workflows.internal.flows.distribution.UpdateDistributionListFlow
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.ObserverAwareFinalityFlow
import com.r3.corda.lib.tokens.workflows.utilities.ourSigningKeys
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap

/**
 * Implements the redeem token flow.
 * The redeemer of the token wants to redeem the token and receive the payment.
 * The flow is initiated by the redeemer.
 * The dealer creates the transaction with payment which is then verified by the redeemer.
 */
@InitiatingFlow
@StartableByRPC
class RedeemDiamondGradingReportFlow(
        private val tokenId: UniqueIdentifier,
        private val redeemer: AccountInfo,
        private val dealer: AccountInfo,
        private val amount: Amount<TokenType>) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        requireThat {"Redeemer not hosted on this node" using (redeemer.host == ourIdentity) }

        if (dealer.host == ourIdentity){
            return RedeemDiamondGradingReportWithinNode().call()
        }

        val other = initiateFlow(dealer.host)

        // Retrieve the redeemer's account details
        val redeemerAccount = getStateReference(serviceHub, AccountInfo::class.java, redeemer.linearId)

        // Send redeemer account info to dealer
        subFlow(ShareAccountInfo(redeemerAccount, listOf(dealer.host)))

        // Retrieve the token to be redeemed
        val original = getStateReference(serviceHub, NonFungibleToken::class.java, tokenId, redeemer)

        // Send key mappings of token holder to dealer
        subFlow(SyncKeyMappingInitiator(dealer.host, listOf(original.state.data.holder)))

        // Send the state reference for the token being redeemed
        subFlow(SendStateAndRefFlow(other, listOf(original)))

        // Send trade details
        other.send(TradeInfo(dealer, redeemer, amount))

        val signedTransactionFlow = object : SignTransactionFlow(other, tracker()){
            override fun checkTransaction(stx: SignedTransaction) {

            }
        }

        val txId = subFlow(signedTransactionFlow)

        subFlow(ObserverAwareFinalityFlowHandler(other))

        return txId
    }

    @Suppress("unused")
    @InitiatedBy(RedeemDiamondGradingReportFlow::class)
    class RedeemDiamondGradingReportFlowResponse (private val flowSession: FlowSession): FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            // Receive the token being redeemed
            val originalToken = subFlow(ReceiveStateAndRefFlow<NonFungibleToken>(flowSession)).single()

            // Receive the trade details
            val tradeInfo = flowSession.receive<TradeInfo>().unwrap { it }

            val redeemer = tradeInfo.redeemer
            val dealer = tradeInfo.dealer

            requireThat {"Dealer not hosted on this node" using (dealer.host == ourIdentity) }

            val dealerParty = serviceHub.createKeyForAccount(dealer)
            val redeemerParty = subFlow(RequestKeyForAccount(redeemer))

            // Define criteria to retrieve only cash from dealer
            val criteria = QueryCriteria.VaultQueryCriteria(
                    status = Vault.StateStatus.UNCONSUMED,
                    externalIds = listOf(dealer.identifier.id)
            )

            val reportId = originalToken.state.data.token.tokenType.tokenIdentifier
            val markerType = TokenType("Marker$reportId", 0)
            val markerState = getToken(serviceHub, markerType, ourIdentity)

            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            val builder = TransactionBuilder(notary)

            // Add payment command from dealer to redeemer
            addMoveFungibleTokensWithFlowException(builder, serviceHub, tradeInfo.price, redeemerParty, dealerParty, criteria)

            // Add redeem token commands
            addTokensToRedeem(builder, listOf(originalToken), null)
            addTokensToRedeem(builder, listOf(markerState), null)

            // Create a list of local signatures for the command
            val signers = builder.toLedgerTransaction(serviceHub).ourSigningKeys(serviceHub) + ourIdentity.owningKey

            // Sign off the transaction
            val selfSignedTransaction = serviceHub.signInitialTransaction(builder, signers)

            // Collect remote signatures
            val fullySignedTransaction = subFlow(CollectSignaturesFlow(selfSignedTransaction, listOf(flowSession)))

            // Notify the notary
            val finalityTransaction = subFlow(ObserverAwareFinalityFlow(fullySignedTransaction, listOf(flowSession)))

            // Update distribution lists
            subFlow(UpdateDistributionListFlow(finalityTransaction))
        }
    }

    /**
     * Use case where both dealer and redeemer are on the same node
     */
    inner class RedeemDiamondGradingReportWithinNode {
        @Suspendable
        fun call(): SignedTransaction {
            val original = getStateReference(serviceHub, NonFungibleToken::class.java, tokenId, redeemer)

            val criteria = QueryCriteria.VaultQueryCriteria(
                    status = Vault.StateStatus.UNCONSUMED,
                    externalIds = listOf(dealer.identifier.id)
            )

            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            val builder = TransactionBuilder(notary)

            val dealerParty = serviceHub.createKeyForAccount(dealer)
            val redeemerParty = serviceHub.createKeyForAccount(redeemer)

            val reportId = original.state.data.token.tokenType.tokenIdentifier
            val markerType = TokenType("Marker$reportId", 0)
            val markerState = getToken(serviceHub, markerType, ourIdentity)

            // Add payment command from dealer to redeemer
            addMoveFungibleTokens(builder, serviceHub, amount, redeemerParty, dealerParty, criteria)

            // Add redeem token commands
            addTokensToRedeem(builder, listOf(original), null)
            addTokensToRedeem(builder, listOf(markerState), null)

            // Create a list of local signatures for the command
            val signers = builder.toLedgerTransaction(serviceHub).ourSigningKeys(serviceHub) + ourIdentity.owningKey

            val selfSignedTransaction = serviceHub.signInitialTransaction(builder, signers)

            val finalityTransaction = subFlow(ObserverAwareFinalityFlow(selfSignedTransaction, emptyList()))

            subFlow(UpdateDistributionListFlow(finalityTransaction))

            return selfSignedTransaction
        }
    }

    @CordaSerializable
    data class TradeInfo(
            val dealer: AccountInfo,
            val redeemer: AccountInfo,
            val price: Amount<TokenType>
    )
}
