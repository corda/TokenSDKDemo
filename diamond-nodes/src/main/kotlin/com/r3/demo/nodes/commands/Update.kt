package com.r3.demo.nodes.commands

import net.corda.core.messaging.startTrackedFlow
import com.r3.demo.nodes.Main
import com.r3.demo.tokens.flows.UpdateDiamondGradingReportFlow

/**
 * Implement the issue command
 */
class Update : Command {
    companion object {
        const val COMMAND = "update"
    }

    /**
     * Execute the report command. Creates a diamond
     * grading report on the ledger.
     * (issuer, requester, caret, clarity, colour, cut)
     *
     * @param main execution context
     * @param array list of command plus arguments
     * @param parameters original command line
     */
    override fun execute(main: Main, array: kotlin.collections.List<String>, parameters: String): Iterator<String> {
        if (array.size < 2){
            return help().listIterator()
        }

        // Get the user who is meant to invoke the command
        val user = main.getUser(array[1])
        val connection = main.getConnection(user)
        val service = connection.proxy
        val linearId = main.retrieveNode(array[2]) ?: throw IllegalArgumentException("Report ID ${array[2]} not found")
        val report = Utilities.parseReport(main, service, parameters, linearId)

        service.startTrackedFlow(::UpdateDiamondGradingReportFlow, linearId, report).returnValue.get()

        // Display the new list of unconsumed states
        val nodes = Nodes()
        val text = "nodes ${array[1]}"

        return nodes.execute(main, text.split(" "), text)
    }

    override fun name(): String {
        return COMMAND
    }

    override fun help(): kotlin.collections.List<String> {
        return listOf("usage: update issuer report-id (issuer, requester, caret, clarity, colour, cut)")
    }
}