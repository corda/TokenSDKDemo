package com.r3.demo.nodes.commands

import com.r3.demo.nodes.Main
import com.r3.demo.tokens.flows.CashIssueFlow
import net.corda.core.messaging.startFlow

class IssueCash : Command {
    companion object {
        const val COMMAND = "issue-cash"
    }

    /**
     * Execute the issue-cash command. Generates cash issued by the user.
     *
     * @param main execution context
     * @param array list of command plus arguments
     * @param parameters original command line
     */
    override fun execute(main: Main, array: List<String>, parameters: String): Iterator<String> {
        if (array.size != 4){
            return help().listIterator()
        }
        val issuer = main.getUser(array[1])
        val connection = main.getConnection(issuer)
        val service = connection.proxy
        val receiver = main.getWellKnownUser(main.getUser(array[2]), service)

        val amount = Utilities.getAmount(array[3])
        val cashState = service.startFlow(::CashIssueFlow, receiver, amount).returnValue.get()

        return listOf(cashState.toString()).listIterator()
    }

    override fun name(): String {
        return COMMAND
    }

    override fun help(): List<String> {
        return listOf("usage: issue-cash <issuer> <receiver> <currency-amount>")
    }
}