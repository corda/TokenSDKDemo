package com.r3.demo.nodes.commands

import net.corda.core.messaging.startTrackedFlow
import com.r3.demo.nodes.Main
import com.r3.demo.tokens.flows.CreateAccountFlow

/**
 * Implement the issue command
 */
class CreateAccount : Command {
    companion object {
        const val COMMAND = "create-account"
    }

    /**
     * Execute the create buyer flow
     *
     * @param main execution context
     * @param array list of command plus arguments
     * @param parameters original command line
     */
    override fun execute(main: Main, array: List<String>, parameters: String): Iterator<String> {
        if (array.size < 3){
            return help().listIterator()
        }

        // Get the node who is meant to invoke the command
        val node = main.retrieveNode(array[1])

        if (!node.isDealer()){
            return listOf("Only dealers are allowed to create accounts").listIterator()
        }

        val connection = main.getConnection(node)
        val service = connection.proxy
        val name = array[2]

        Utilities.logStart()

        val state = service.startTrackedFlow(::CreateAccountFlow, name).returnValue.get()
        val account = state.state.data

        Utilities.logFinish()

        main.registerAccount(account.name, account)
        main.registerNode(account, node)

        return listOf("Account ${account.name} on [${account.host.name}] with id ${account.identifier.id}").listIterator()
    }

    override fun name(): String {
        return COMMAND
    }

    override fun help(): List<String> {
        return listOf("usage: create-account dealer name")
    }

    override fun description(): String {
        return "Create a diamond trading account"
    }
}