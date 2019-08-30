package com.r3.demo.nodes.commands

import net.corda.core.messaging.startTrackedFlow
import com.r3.demo.tokens.flows.CreateDiamondGradingReportFlow
import com.r3.demo.nodes.Main
import com.r3.demo.nodes.User

/**
 * Implement the issue command
 */
class Create : Command {
    companion object {
        const val COMMAND = "create"
    }

    /**
     * Execute the report command. Creates a diamond
     * grading report on the ledger.
     * (issuer, dealer, caret, clarity, colour, cut)
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

        if (!user.isCertifier()){
            return listOf("Only graders are allowed to create reports").listIterator()
        }

        val connection = main.getConnection(user)
        val service = connection.proxy
        val report = Utilities.parseReport(main, service, parameters)

        Utilities.logStart()

        service.startTrackedFlow(::CreateDiamondGradingReportFlow, report).returnValue.get()

        Utilities.logFinish()

        // Display the new list of unconsumed states
        val nodes = Nodes()
        val text = "nodes ${array[1]}"

        return nodes.execute(main, text.split(" "), text)
    }

    override fun name(): String {
        return COMMAND
    }

    override fun help(): kotlin.collections.List<String> {
        return listOf("usage: create issuer (issuer, dealer, caret, clarity, colour, cut)")
    }

    override fun description(): String {
        return "Create a diamond grade report"
    }
}