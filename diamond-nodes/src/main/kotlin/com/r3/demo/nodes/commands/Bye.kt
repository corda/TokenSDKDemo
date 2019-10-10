package com.r3.demo.nodes.commands

import com.r3.demo.nodes.Main

/**
 * Implement the bye command
 */
class Bye : Command {
    companion object {
        const val COMMAND = "bye"
    }

    /**
     * Execute the bye command. Exits the application.
     *
     * @param main execution context
     * @param array list of command plus arguments
     * @param parameters original command line
     */
    override fun execute(main: Main, array: List<String>, parameters: String): Iterator<String> {
        if (array.size != 1){
            return help().listIterator()
        }

        System.exit(0)

        return listOf("").listIterator()
    }

    override fun name(): String {
        return COMMAND
    }

    override fun help(): List<String> {
        return listOf("usage: bye")
    }

    override fun description(): String {
        return "Exit client"
    }
}