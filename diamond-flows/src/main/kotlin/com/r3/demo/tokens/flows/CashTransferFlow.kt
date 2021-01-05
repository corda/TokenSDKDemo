package com.r3.demo.tokens.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount
import com.r3.corda.lib.accounts.workflows.internal.flows.createKeyForAccount
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.workflows.flows.move.MoveTokensFlowHandler
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.ObserverAwareFinalityFlow
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveFungibleTokens
import com.r3.corda.lib.tokens.workflows.utilities.ourSigningKeys
import net.corda.core.contracts.Amount
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

/**
 * Implements the pay cash flow.
 */
@InitiatingFlow
@StartableByRPC
class CashTransferFlow(
        private val payer: AccountInfo,
        private val receiver: AccountInfo,
        private val amount: Amount<TokenType>) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        requireThat {"Payer not hosted on this node" using (payer.host == ourIdentity) }

        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val builder = TransactionBuilder(notary)

        // Create a payer party to receive any change from the transfer
        val payerParty = serviceHub.createKeyForAccount(payer)

        // Create a receiver party for the transaction. If receiver not on this
        // node then use the sub-flow to generate the party
        val receiverParty =
                if (receiver.host == ourIdentity) {
                    serviceHub.createKeyForAccount(receiver)
                } else {
                    subFlow(RequestKeyForAccount(receiver))
                }

        // Define criteria to retrieve only cash from payer
        val criteria = QueryCriteria.VaultQueryCriteria(
                status = Vault.StateStatus.UNCONSUMED,
                externalIds = listOf(payer.identifier.id)
        )

        // Build transaction to pay the buyer based on the criteria, giving the change to the payer
        addMoveFungibleTokens(builder, serviceHub, amount, receiverParty, payerParty, criteria)

        // Retrieve the list of signers for the transaction
        val signers = builder.toLedgerTransaction(serviceHub).ourSigningKeys(serviceHub) + ourIdentity.owningKey

        // Self sign the transaction with signatures found on the command
        val signedTransaction = serviceHub.signInitialTransaction(builder, signers)

        val flow = initiateFlow(receiver.host)
        val flows = listOf(flow)

        return subFlow(ObserverAwareFinalityFlow(signedTransaction, flows))
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


