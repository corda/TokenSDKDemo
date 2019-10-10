package com.r3.demo.nodes.commands

import com.r3.demo.nodes.Main
import kotlin.collections.List

/**
 * Implement the whoami command
 */
class Whoami : Command {
    companion object {
        const val COMMAND = "whoami"
    }

    /**
     * Execute the whoami command.
     *
     * @param main execution context
     * @param array list of command plus arguments
     * @param parameters original command line
     */
    override fun execute(main: Main, array: List<String>, parameters: String): Iterator<String> {
        if (array.size != 2){
            return help().listIterator()
        }
        val user = main.retrieveNode(array[1])
        val connection = main.getConnection(user)
        val service = connection.proxy

        return listOf(service.nodeInfo().legalIdentities.first().name.toString()).listIterator()
    }

    override fun name(): String {
        return COMMAND
    }

    override fun help(): List<String> {
        return listOf("usage: whoami <user>")
    }
}