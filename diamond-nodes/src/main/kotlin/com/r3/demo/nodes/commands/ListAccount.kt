package com.r3.demo.nodes.commands

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.demo.nodes.Main
import com.r3.demo.tokens.flows.RetrieveWalletFlow
import com.r3.demo.tokens.state.DiamondGradingReport
import net.corda.core.messaging.startTrackedFlow

/**
 * Implement the list account command
 */
class ListAccount : Command {
    companion object {
        const val COMMAND = "list"
    }

    /**
     * Execute the list command. Displays the unconsumed states from a node's vault for an buyer.
     *
     * @param main execution context
     * @param array list of command plus arguments
     * @param parameters original command line
     */
    override fun execute(main: Main, array: List<String>, parameters: String): Iterator<String> {
        if (array.size != 2){
            return help().listIterator()
        }

        val accountInfo = main.retrieveAccount(array[1])

        val node = main.retrieveNode(accountInfo)
        val connection = main.getConnection(node)
        val service = connection.proxy
        val list = mutableListOf<String>()


        Utilities.logStart()

        val vaultPage = service.startTrackedFlow(::RetrieveWalletFlow, accountInfo).returnValue.get()
        Utilities.logFinish()

        val states = vaultPage.map { it.state.data }

        states.filterIsInstance<DiamondGradingReport>().forEach {
            main.registerState(it.linearId.toString(), it.linearId)
            list.add("${it.linearId.toString().substring(0, 8)} = ${it.printReport()}")
        }

        states.filterIsInstance<NonFungibleToken>().forEach {
            main.registerState(it.linearId.toString(), it.linearId)
            list.add("${it.linearId.toString().substring(0, 8)} = (${it.printReport()})")
        }

        states.filterIsInstance<FungibleToken>().forEach {
            list.add("${it}")
        }

        return list.iterator()
    }

    override fun name(): String {
        return COMMAND
    }

    override fun description(): String {
        return "List unconsumed tokens"
    }

    override fun help(): List<String> {
        return listOf("usage: list account")
    }
}