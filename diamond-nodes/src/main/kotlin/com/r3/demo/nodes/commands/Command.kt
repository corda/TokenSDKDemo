package com.r3.demo.nodes.commands

import com.r3.demo.nodes.Main

/**
 * All commands implement this interface
 */
interface Command {
    /**
     * Execute the command.
     *
     * @param main execution context
     * @param array list of command plus arguments
     * @param parameters original command line
     */
    fun execute(main: Main, array: List<String>, parameters: String): Iterator<String>

    /**
     * Return help
     */
    fun help(): List<String>

    /**
     * Return help
     */
    fun description(): String { return "" }

    /**
     * Return command name
     */
    fun name(): String
}
