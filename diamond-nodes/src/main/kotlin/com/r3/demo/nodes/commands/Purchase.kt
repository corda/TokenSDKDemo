package com.r3.demo.nodes.commands

import net.corda.core.messaging.startTrackedFlow
import com.r3.demo.nodes.Main
import com.r3.demo.tokens.flows.PurchaseDiamondGradingReportFlow

/**
 * Implement the issue command
 */
class Purchase : Command {
    companion object {
        const val COMMAND = "purchase"
    }

    /**
     * Execute the issue command. Issues a report to a dealer.
     *
     * @param main execution context
     * @param array list of command plus arguments
     * @param parameters original command line
     */
    override fun execute(main: Main, array: kotlin.collections.List<String>, parameters: String): Iterator<String> {
        if (array.size < 5){
            return help().listIterator()
        }

        // Get the user who is meant to invoke the command
        val dealer = main.getUser(array[1])

        if (!dealer.isDealer()){
            return listOf("Only dealers are allowed to sell diamonds").listIterator()
        }

        val connection = main.getConnection(dealer)
        val service = connection.proxy
        val buyer = main.getWellKnownUser(main.getUser(array[2]), service)
        val linearId = main.retrieveNode(array[3]) ?: throw IllegalArgumentException("Report ID ${array[3]} not found")

        val amount = Utilities.getAmount(array[4])

        Utilities.logStart()

        service.startTrackedFlow(::PurchaseDiamondGradingReportFlow, linearId, buyer, amount).returnValue.get()

        Utilities.logFinish()

        // Display the new list of unconsumed states
        val nodes = Nodes()
        val text = "nodes ${array[1]}"

        return nodes.execute(main, text.split(" "), text)
    }

    override fun name(): String {
        return COMMAND
    }

    override fun description(): String {
        return "Purchase a diamond token for cash from a report"
    }

    override fun help(): kotlin.collections.List<String> {
        return listOf("usage: purchase dealer buyer report-id payment")
    }
}