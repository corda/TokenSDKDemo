package com.r3.demo.nodes.commands

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.demo.nodes.Main
import com.r3.demo.tokens.flows.RetrieveWalletFlow
import com.r3.demo.tokens.state.DiamondGradingReport
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.messaging.vaultQueryBy

/**
 * Implement the list account command
 */
class ListNode : Command {
    companion object {
        const val COMMAND = "reports"
    }

    /**
     * Execute the reports command. Displays the reports on a node.
     *
     * @param main execution context
     * @param array list of command plus arguments
     * @param parameters original command line
     */
    override fun execute(main: Main, array: List<String>, parameters: String): Iterator<String> {
        if (array.size != 2){
            return help().listIterator()
        }

        // Get the user who is meant to invoke the command
        val node = main.retrieveNode(array[1])

        val connection = main.getConnection(node)
        val service = connection.proxy
        val list = mutableListOf<String>()

        Utilities.logStart()

        val vaultPage = service.vaultQueryBy<DiamondGradingReport>().states
        val states = vaultPage.map { it.state.data }

        Utilities.logFinish()

        // Record the linear id against the name for easy reference
        states.forEach{
            main.registerState(it.linearId.toString(), it.linearId)
            list.add("${it.linearId.toString().substring(0, 8)} = ${it.printReport()}")
        }

        return list.iterator()
    }

    override fun name(): String {
        return COMMAND
    }

    override fun description(): String {
        return "List reports"
    }

    override fun help(): List<String> {
        return listOf("usage: reports node")
    }
}