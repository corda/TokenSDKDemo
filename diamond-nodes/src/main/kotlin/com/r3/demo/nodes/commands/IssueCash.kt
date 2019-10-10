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
        val issuer = main.retrieveNode(array[1])

        if (!issuer.isIssuer()){
            return listOf("Only banks are allowed to issue cash").listIterator()
        }

        val connection = main.getConnection(issuer)
        val service = connection.proxy
        val receiver = main.retrieveAccount(array[2])

        val amount = Utilities.getAmount(array[3])

        Utilities.logStart()

        val cashState = service.startFlow(::CashIssueFlow, receiver, amount).returnValue.get()

        Utilities.logFinish()

        return listOf(cashState.toString()).listIterator()
    }

    override fun name(): String {
        return COMMAND
    }

    override fun description(): String {
        return "Issue cash from a bank"
    }

    override fun help(): List<String> {
        return listOf("usage: issue-cash bank account amount")
    }
}