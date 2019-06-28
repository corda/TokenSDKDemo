package com.r3.demo.tokens.flows

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.vault.QueryCriteria

fun <T : ContractState> getStateReference(serviceHub: ServiceHub, clazz: Class<T>, id: UniqueIdentifier): StateAndRef<T> {
    val vaultPage = serviceHub.vaultService.queryBy(clazz,
            QueryCriteria.LinearStateQueryCriteria(linearId = listOf(id)))

    requireThat {
        "State not found" using (vaultPage.states.size == 1)
    }

    return vaultPage.states.first()
}

