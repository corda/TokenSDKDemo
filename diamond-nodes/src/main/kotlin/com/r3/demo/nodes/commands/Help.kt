package com.r3.demo.nodes.commands

import com.r3.demo.nodes.Main

/**
 * Implement the help command
 */
class Help  : Command {
    companion object {
        const val COMMAND = "help"
    }

    /**
     * Execute the help command.
     *
     * @param main execution context
     * @param array list of command plus arguments
     * @param parameters original command line
     */
    override fun execute(main: Main, array: List<String>, parameters: String): Iterator<String> {
        when (array.size){
            1 -> {
                return main.commandMap.values.map {
                    command -> "${command.name()}: ${command.description()}"
                }.sorted().listIterator()
            }
            2 -> {
                val command = main.commandMap[array[1].toLowerCase()]
                return command?.help()?.listIterator() ?: listOf("unknown commands '${array[1]}'").listIterator()
            }
            else -> return help().listIterator()
        }
    }

    override fun name(): String {
        return COMMAND
    }

    override fun description(): String {
        return "Provide help"
    }

    override fun help(): List<String> {
        return listOf("usage: help [commands]")
    }
}