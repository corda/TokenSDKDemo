package com.r3.demo.nodes.commands

import com.r3.demo.nodes.Main
import com.r3.vaultrecycler.explorer.flows.runPagedExplorer
import java.io.File

/**
 * Implement the explore command
 */
class Explore : Command {
    companion object {
        const val COMMAND = "explore"
        const val FILENAME = "explore.txt"
    }

    /**
     * Execute the explore command. Runs the vault explore on a user's vault.
     *
     * @param main execution context
     * @param array list of command plus arguments
     * @param parameters original command line
     */
    override fun execute(main: Main, array: List<String>, parameters: String): Iterator<String> {
        if (array.size < 2){
            return help().listIterator()
        }
        val user = main.getUser(array[1])
        val connection = main.getConnection(user)
        val service = connection.proxy

        val size = getPageSize(array)
        val explorers = getExplorers(array)

        val data = service.runPagedExplorer(FILENAME, size, explorers)

        // The results are returned as a zip stream using the file name given above
        Utilities.expandZipArray(data)

        // The file is then read from disk
        val list = ArrayList<String>()
        val bufferedReader = File(FILENAME).bufferedReader()

        bufferedReader.useLines { list.addAll(it) }

        return list.listIterator()
    }

    override fun name(): String {
        return COMMAND
    }

    override fun help(): List<String> {
        return listOf("usage: explore <user> [page-size] [explorer ...]")
    }

    /**
     * Parse the page size parameter if present
     */
    private fun getPageSize(array: List<String>): Int {
        return if (array.size > 2) array[2].toIntOrNull() ?: 0 else 0
    }

    /**
     * Parse the explorer parameters if present
     */
    private fun getExplorers(array: List<String>): String {
        return if (array.size > 2) {
            if (array[2].toIntOrNull() == null){
                array.subList(2,array.size).joinToString(",")
            } else if (array.size > 3){
                array.subList(3,array.size).joinToString(",")
            } else {
                ""
            }
        } else {
            return ""
        }
    }
}