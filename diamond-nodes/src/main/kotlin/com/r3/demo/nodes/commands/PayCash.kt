package com.r3.demo.nodes.commands

import net.corda.core.messaging.startTrackedFlow
import com.r3.demo.nodes.Main
import com.r3.demo.tokens.flows.CashTransferFlow

/**
 * Implement the pay-cash command
 */
class PayCash : Command {
    companion object {
        const val COMMAND = "pay-cash"
    }

    /**
     * Execute the pay-cash command.
     *
     * @param main execution context
     * @param array list of command plus arguments
     * @param parameters original command line
     */
    override fun execute(main: Main, array: kotlin.collections.List<String>, parameters: String): Iterator<String> {
        if (array.size < 4){
            return help().listIterator()
        }

        // Get the user who is meant to invoke the command
        val payer = main.getUser(array[1])
        val connection = main.getConnection(payer)
        val service = connection.proxy
        val receiver = main.getWellKnownUser(main.getUser(array[2]), service)

        val amount = Utilities.getAmount(array[3])

        service.startTrackedFlow(::CashTransferFlow, receiver, amount).returnValue.get()

        // Display the new list of unconsumed states
        val nodes = Nodes()
        val text = "nodes ${array[1]}"

        return nodes.execute(main, text.split(" "), text)
    }

    override fun name(): String {
        return COMMAND
    }

    override fun help(): kotlin.collections.List<String> {
        return listOf("usage: pay-cash payer receiver amount")
    }
}