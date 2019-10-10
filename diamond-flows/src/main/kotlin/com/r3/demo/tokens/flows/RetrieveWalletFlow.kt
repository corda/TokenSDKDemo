package com.r3.demo.tokens.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria

@StartableByRPC
class RetrieveWalletFlow(private val account: AccountInfo) : FlowLogic<List<StateAndRef<ContractState>>>() {
    @Suspendable
    override fun call(): List<StateAndRef<ContractState>> {
        val criteria = QueryCriteria.VaultQueryCriteria(
            status = Vault.StateStatus.UNCONSUMED,
            externalIds = listOf(account.identifier.id)
        )
        return serviceHub.vaultService.queryBy<ContractState>(criteria = criteria).states
    }
}