package com.r3.demo.tokens.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveFungibleTokens
import net.corda.core.contracts.*
import net.corda.core.flows.FlowException
import net.corda.core.identity.AbstractParty
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.TransactionBuilder

/**
 * Retrieve a state reference using linear id.
 */
fun <T : ContractState> getStateReference(serviceHub: ServiceHub, clazz: Class<T>, id: UniqueIdentifier): StateAndRef<T> {
    val vaultPage = serviceHub.vaultService.queryBy(clazz,
            QueryCriteria.LinearStateQueryCriteria(linearId = listOf(id)))

    requireThat {
        "State not found" using (vaultPage.states.size == 1)
    }

    return vaultPage.states.first()
}

/**
 * Wraps the standard move token method so that insufficient funds exception
 * is propagated back to the caller as a [FlowException]
 */
@Suspendable
fun addMoveFungibleTokensWithFlowException(builder: TransactionBuilder, serviceHub: ServiceHub, price: Amount<TokenType>, sellerParty: AbstractParty, buyerParty: AbstractParty, criteria: QueryCriteria.VaultQueryCriteria){
    try {
        addMoveFungibleTokens(builder, serviceHub, price, sellerParty, buyerParty, criteria)
    } catch (e: Exception){
        throw FlowException(e.toString())
    }
}