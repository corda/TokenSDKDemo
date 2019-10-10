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
    override fun execute(main: Main, array: List<String>, parameters: String): Iterator<String> {
        if (array.size < 4){
            return help().listIterator()
        }

        val payerInfo = main.retrieveAccount(array[1])
        val receiverInfo = main.retrieveAccount(array[2])

        // Get the node of the receiver which is meant to invoke the command
        val payer = main.retrieveNode(payerInfo)
        val connection = main.getConnection(payer)
        val service = connection.proxy

        val amount = Utilities.getAmount(array[3])

        service.startTrackedFlow(::CashTransferFlow, payerInfo, receiverInfo, amount).returnValue.get()

        // Display the new list of unconsumed states
        val nodes = ListAccount()
        val text = "list ${array[1]}"

        return nodes.execute(main, text.split(" "), text)
    }

    override fun name(): String {
        return COMMAND
    }

    override fun description(): String {
        return "Pay cash between accounts"
    }

    override fun help(): List<String> {
        return listOf("usage: pay-cash payer receiver amount")
    }
}