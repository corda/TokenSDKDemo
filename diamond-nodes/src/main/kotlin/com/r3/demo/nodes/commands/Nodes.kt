package com.r3.demo.nodes.commands

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.messaging.vaultQueryBy
import com.r3.demo.nodes.Main
import com.r3.demo.tokens.state.DiamondGradingReport
import kotlin.collections.List

/**
 * Implement the nodes command
 */
class Nodes : Command {
    companion object {
        const val COMMAND = "list"
    }

    /**
     * Execute the nodes command. Displays the unconsumed states from a user's vault.
     *
     * @param main execution context
     * @param array list of command plus arguments
     * @param parameters original command line
     */
    override fun execute(main: Main, array: List<String>, parameters: String): Iterator<String> {
        if (array.size != 2){
            return help().listIterator()
        }
        val user = main.getUser(array[1])
        val connection = main.getConnection(user)
        val service = connection.proxy
        val list = mutableListOf<String>()

        // Get the list of unconsumed states from the vault
        val vaultPage = service.vaultQueryBy<DiamondGradingReport>().states
        val states = vaultPage.map { it.state.data }

        // Record the linear id against the name for easy reference
        states.forEach{
            main.registerNode(it.linearId.toString(), it.linearId)
            list.add(it.printReport())
        }

        // Get the list of unconsumed token pointers from the vault
        val tokenPage = service.vaultQueryBy<NonFungibleToken<TokenType>>().states
        val tokens = tokenPage.map { it.state.data }

        // Record the linear id against the name for easy reference
        tokens.forEach{
            main.registerNode(it.linearId.toString(), it.linearId)
            list.add("${it.linearId} = (${it})")
        }

        // Get the list of unconsumed token pointers from the vault
        val moneyPage = service.vaultQueryBy<FungibleToken<TokenType>>().states
        val monies = moneyPage.map { it.state.data }

        // Record the linear id against the name for easy reference
        monies.forEach{
            list.add("${it}")
        }

        return list.iterator()
    }

    override fun name(): String {
        return COMMAND
    }

    override fun help(): List<String> {
        return listOf("usage: list <user>")
    }
}