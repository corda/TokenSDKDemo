package com.r3.demo.tokens.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC

/**
 * Returns a list of accounts
 */
@StartableByRPC
class RetrieveAccountsFlow : FlowLogic<List<AccountInfo>>() {
    @Suspendable
    override fun call(): List<AccountInfo> {
        val vault = serviceHub.vaultService.queryBy(AccountInfo::class.java)

        return vault.states.map { it.state.data }.filter { it.host == ourIdentity }
    }
}

