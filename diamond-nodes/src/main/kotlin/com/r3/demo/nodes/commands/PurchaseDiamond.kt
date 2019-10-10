package com.r3.demo.nodes.commands

import net.corda.core.messaging.startTrackedFlow
import com.r3.demo.nodes.Main
import com.r3.demo.tokens.flows.PurchaseDiamondGradingReportFlow

/**
 * Implement the purchase command
 */
class PurchaseDiamond : Command {
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
    override fun execute(main: Main, array: List<String>, parameters: String): Iterator<String> {
        if (array.size < 5){
            return help().listIterator()
        }

        // Get the user who is meant to invoke the command
        val dealer = main.retrieveAccount(array[1])
        val node = main.retrieveNode(dealer)

        if (!node.isDealer()){
            return listOf("Only dealers are allowed to sell diamonds").listIterator()
        }

        val connection = main.getConnection(node)
        val service = connection.proxy
        val buyer = main.retrieveAccount(array[2])
        val linearId = main.retrieveState(array[3]) ?: throw IllegalArgumentException("Report ID ${array[3]} not found")

        val amount = Utilities.getAmount(array[4])

        Utilities.logStart()

        service.startTrackedFlow(::PurchaseDiamondGradingReportFlow, linearId, dealer, buyer, amount).returnValue.get()

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
        return "Purchase a diamond token for cash from a report"
    }

    override fun help(): List<String> {
        return listOf("usage: purchase dealer buyer report-id payment")
    }
}