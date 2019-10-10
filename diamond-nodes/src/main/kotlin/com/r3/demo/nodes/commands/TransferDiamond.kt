package com.r3.demo.nodes.commands

import net.corda.core.messaging.startTrackedFlow
import com.r3.demo.nodes.Main
import com.r3.demo.tokens.flows.TransferDiamondGradingReportFlow

/**
 * Implement the issue command
 */
class TransferDiamond : Command {
    companion object {
        const val COMMAND = "transfer"
    }

    /**
     * Execute the move command. Moves an issue between users.
     *
     * @param main execution context
     * @param array list of command plus arguments
     * @param parameters original command line
     */
    override fun execute(main: Main, array: List<String>, parameters: String): Iterator<String> {
        if (array.size < 5){
            return help().listIterator()
        }

        val seller = main.retrieveAccount(array[1])
        val buyer = main.retrieveAccount(array[2])

        val connection = main.getConnection(main.retrieveNode(seller))
        val service = connection.proxy

        val tokenId = main.retrieveState(array[3]) ?: throw IllegalArgumentException("Token ID ${array[3]} not found")
        val amount = Utilities.getAmount(array[4])

        Utilities.logStart()

        service.startTrackedFlow(::TransferDiamondGradingReportFlow, tokenId, seller, buyer, amount).returnValue.get()

        Utilities.logFinish()

        // Display the new list of unconsumed states
        val nodes = ListAccount()
        val text = "list ${array[1]}"

        return nodes.execute(main, text.split(" "), text)
    }

    override fun name(): String {
        return COMMAND
    }

    override fun description(): String {
        return "Sell a diamond token"
    }

    override fun help(): List<String> {
        return listOf("usage: transfer seller buyer token-id payment")
    }
}