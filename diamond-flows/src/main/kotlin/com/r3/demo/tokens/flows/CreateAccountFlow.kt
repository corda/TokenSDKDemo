package com.r3.demo.tokens.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount
import com.r3.demo.tokens.services.DirectoryService
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC

@InitiatingFlow
@StartableByRPC
/**
 * A wrapper around the main create account flow so that the account info can be saved
 * in the directory service
 */
class CreateAccountFlow(private val name: String) : FlowLogic<StateAndRef<AccountInfo>>() {
    @Suspendable
    override fun call(): StateAndRef<AccountInfo> {
        val state = subFlow(CreateAccount(name))

        val accountInfo = state.state.data
        val directoryService = serviceHub.cordaService(DirectoryService::class.java)

        directoryService.recordAccount(accountInfo)

        return state
    }
}
