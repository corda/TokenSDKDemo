package com.r3.demo.tokens.flows

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.contextLogger
import java.util.concurrent.atomic.AtomicLong

fun <T : ContractState> getStateReference(serviceHub: ServiceHub, clazz: Class<T>, id: UniqueIdentifier): StateAndRef<T> {
    val vaultPage = serviceHub.vaultService.queryBy(clazz,
            QueryCriteria.LinearStateQueryCriteria(linearId = listOf(id)))

    requireThat {
        "State not found" using (vaultPage.states.size == 1)
    }

    return vaultPage.states.first()
}

fun logTime(serviceHub: ServiceHub, logstart: AtomicLong, message: String){
    val logTime = System.currentTimeMillis() - logstart.get()

    serviceHub.contextLogger().info("TTT - ${message} = ${logTime}ms")

    logstart.set(System.currentTimeMillis())
}

