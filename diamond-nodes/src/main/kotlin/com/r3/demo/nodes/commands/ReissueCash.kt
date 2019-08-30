package com.r3.demo.nodes.commands

import com.r3.demo.nodes.Main
import com.r3.demo.tokens.flows.CashIssueFlow
import com.r3.demo.tokens.flows.CashReissueFlow
import net.corda.core.messaging.startFlow

class ReissueCash : Command {
    companion object {
        const val COMMAND = "reissue-cash"
    }

    /**
     * Execute the reissue-cash command. Generates cash issued by the user.
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

        if (!issuer.isIssuer()){
            return listOf("Only bank issuers are allowed to issue cash").listIterator()
        }

        val receiver = main.getUser(array[2])

        val connection = main.getConnection(receiver)
        val service = connection.proxy
        val amount = Utilities.getAmount(array[3])

        Utilities.logStart()

        val cashState = service.startFlow(::CashReissueFlow, main.getWellKnownUser(issuer, service), amount).returnValue.get()

        Utilities.logFinish()

        return listOf(cashState.toString()).listIterator()
    }

    override fun name(): String {
        return COMMAND
    }

    override fun description(): String {
        return "Reissue cash from an issuer"
    }

    override fun help(): List<String> {
        return listOf("usage: reissue-cash <issuer> <receiver> <currency-amount>")
    }
}