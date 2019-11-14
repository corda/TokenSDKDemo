package com.r3.demo.tokens.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveFungibleTokens
import com.r3.corda.lib.tokens.workflows.utilities.heldTokensByTokenIssuer
import net.corda.core.contracts.*
import net.corda.core.flows.FlowException
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.TransactionBuilder

/**
 * Retrieve a state reference using a linear id.
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
 * Retrieve a state reference using a linear id belonging to an account.
 */
fun <T : ContractState> getStateReference(serviceHub: ServiceHub, clazz: Class<T>, id: UniqueIdentifier, accountInfo: AccountInfo): StateAndRef<T> {
    val externalCriteria = QueryCriteria.VaultQueryCriteria(
            status = Vault.StateStatus.UNCONSUMED,
            externalIds = listOf(accountInfo.identifier.id)
    )
    val query = externalCriteria.and(QueryCriteria.LinearStateQueryCriteria(linearId = listOf(id)))
    val vaultPage = serviceHub.vaultService.queryBy(clazz, query)

    requireThat {
        "State not found" using (vaultPage.states.size == 1)
    }

    return vaultPage.states.first()
}

/**
 * Return true if the token is not present
 */
fun hasNoToken(serviceHub: ServiceHub, tokenType: TokenType, issuer: Party): Boolean {
    return serviceHub.vaultService.heldTokensByTokenIssuer(tokenType, issuer).states.isEmpty()
}

/**
 * Return the state reference for the token.
 */
fun getToken(serviceHub: ServiceHub, tokenType: TokenType, issuer: Party): StateAndRef<NonFungibleToken> {
    return serviceHub.vaultService.heldTokensByTokenIssuer(tokenType, issuer).states.single()
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