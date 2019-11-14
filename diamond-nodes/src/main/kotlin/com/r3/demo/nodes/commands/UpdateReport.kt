package com.r3.demo.nodes.commands

import net.corda.core.messaging.startTrackedFlow
import com.r3.demo.nodes.Main
import com.r3.demo.tokens.flows.UpdateDiamondGradingReportFlow
import com.r3.demo.tokens.state.DiamondGradingReport

/**
 * Implement the issue command
 */
class UpdateReport : Command {
    companion object {
        const val COMMAND = "update-report"
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
    override fun execute(main: Main, array: List<String>, parameters: String): Iterator<String> {
        if (array.size < 2){
            return help().listIterator()
        }

        val node = main.retrieveNode(array[1])

        if (!node.isCertifier()){
            return listOf("Only graders are allowed to create reports").listIterator()
        }

        // Get the user who is meant to invoke the command
        val connection = main.getConnection(node)
        val service = connection.proxy
        val linearId = main.retrieveState(array[2]) ?: throw IllegalArgumentException("Report ID ${array[2]} not found")
        val report = Utilities.parseReport(main, parameters, linearId)

        Utilities.logStart()

        val tx = service.startTrackedFlow(::UpdateDiamondGradingReportFlow, linearId, report).returnValue.get()

        Utilities.logFinish()

        val list = tx.tx.outputStates.filterIsInstance(DiamondGradingReport::class.java)
                .map { it.linearId }
                .onEach { main.registerState(it.toString(), it) }
                .map { it.toString() }

        return list.listIterator()
    }

    override fun name(): String {
        return COMMAND
    }

    override fun description(): String {
        return "Update a diamond grade report"
    }

    override fun help(): List<String> {
        return listOf("usage: update-report issuer report-id (issuer, dealer, caret, clarity, colour, cut)")
    }
}