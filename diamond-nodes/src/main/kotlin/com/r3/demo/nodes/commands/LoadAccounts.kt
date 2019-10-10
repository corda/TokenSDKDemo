package com.r3.demo.nodes.commands

import net.corda.core.messaging.startTrackedFlow
import com.r3.demo.nodes.Main
import com.r3.demo.tokens.flows.RetrieveAccountsFlow

/**
 * Implement the issue command
 */
class LoadAccounts : Command {
    companion object {
        const val COMMAND = "load-accounts"
    }

    /**
     * Execute the retrieve accounts flow
     *
     * @param main execution context
     * @param array list of command plus arguments
     * @param parameters original command line
     */
    override fun execute(main: Main, array: List<String>, parameters: String): Iterator<String> {
        if (array.size < 2){
            return help().listIterator()
        }

        // Get the node who is meant to invoke the command
        val node = main.retrieveNode(array[1])

        if (!node.isDealer()){
            return listOf("Only dealers are allowed to have accounts").listIterator()
        }

        val connection = main.getConnection(node)
        val service = connection.proxy

        Utilities.logStart()

        val states = service.startTrackedFlow(::RetrieveAccountsFlow).returnValue.get()

        Utilities.logFinish()

        val list = mutableListOf<String>()

        states.forEach {account ->
            main.registerAccount(account.name, account)
            main.registerNode(account, node)
            list.add("${account.name} on [${account.host.name}] with id ${account.identifier.id}")
        }

        return list.listIterator()
    }

    override fun name(): String {
        return COMMAND
    }

    override fun help(): List<String> {
        return listOf("usage: load-accounts dealer")
    }

    override fun description(): String {
        return "Load account information from a dealer"
    }
}