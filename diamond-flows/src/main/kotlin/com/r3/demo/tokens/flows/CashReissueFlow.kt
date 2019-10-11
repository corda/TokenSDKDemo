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
import com.r3.corda.lib.tokens.workflows.flows.redeem.addFungibleTokensToRedeem
import com.r3.corda.lib.tokens.workflows.utilities.ourSigningKeys
import net.corda.core.contracts.Amount
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap

@InitiatingFlow
@StartableByRPC
/**
 * Reissues the amount of cash in the desired currency and pays the receiver
 */
class CashReissueFlow(
        private val redeemer: AccountInfo,
        private val issuer: Party,
        private val amount: Amount<TokenType>) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        requireThat {"Redeemer not hosted on this node" using (redeemer.host == ourIdentity) }

        // Generate party and key for the transaction
        val owningParty = createKeyForAccount(redeemer, serviceHub)

        // Define criteria to retrieve only cash from redeemer
        val criteria = QueryCriteria.VaultQueryCriteria(
                status = Vault.StateStatus.UNCONSUMED,
                externalIds = listOf(redeemer.identifier.id)
        )

        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val builder = TransactionBuilder(notary)

        // Create transaction to redeem the cash
        addFungibleTokensToRedeem(builder, serviceHub, amount, issuer, owningParty, criteria)

        val signers = builder.toLedgerTransaction(serviceHub).ourSigningKeys(serviceHub) + ourIdentity.owningKey

        // Self sign the transaction
        val selfSignedTransaction = serviceHub.signInitialTransaction(builder, signers)

        val redeemFlow = initiateFlow(issuer)
        val redeemFlows = listOf(redeemFlow)

        // Get issuer to sign the transaction
        val fullySignedTransaction = subFlow(CollectSignaturesFlow(selfSignedTransaction, redeemFlows))

        // Notify the notary
        subFlow(FinalityFlow(fullySignedTransaction, redeemFlows))

        // Send details of issue token to issuer
        redeemFlow.send(Info(redeemer, fullySignedTransaction, amount))

        // Handle the issue token transaction
        subFlow(IssueTokensFlowHandler(redeemFlow))

        // Receive issue token transaction from issuer
        val txInfo = redeemFlow.receive<Info>().unwrap { it }

        // Return transaction id
        return txInfo.txId
    }

    @Suppress("unused")
    @InitiatedBy(CashReissueFlow::class)
    class CashReissueFlowResponse (val flowSession: FlowSession): FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val signedTransactionFlow = object : SignTransactionFlow(flowSession, tracker()){
                override fun checkTransaction(stx: SignedTransaction) {
                    // Perform validation
                }
            }

            // Issuer signs the redemption transaction
            val signedTransaction = subFlow(signedTransactionFlow)

            // Issuer handles the finality flow
            subFlow(ReceiveFinalityFlow(flowSession, expectedTxId = signedTransaction.id))

            // Receive the reissue details from user
            val info = flowSession.receive<Info>().unwrap { it }

            val owningParty =
                    if (info.account.host == ourIdentity) {
                        createKeyForAccount(info.account, serviceHub)
                    } else {
                        subFlow(RequestKeyForAccount(info.account))
                    }
            // Reissue the fungible token
            val token = info.amount issuedBy ourIdentity heldBy AnonymousParty(owningParty.owningKey)

            val flow = IssueTokensFlow(token, listOf(flowSession), emptyList())

            val issueTransaction = subFlow(flow)

            // Send back issue token transaction to user
            flowSession.send(Info(info.account, issueTransaction, info.amount))
        }
    }

    @CordaSerializable
    data class Info(
            val account: AccountInfo,
            val txId: SignedTransaction,
            val amount: Amount<TokenType>
    )
}

