package com.r3.demo.nodes.commands

import net.corda.core.node.NodeInfo
import com.r3.demo.nodes.Main
import kotlin.collections.List

/**
 * Implement the peers command
 */
class Peers : Command {
    companion object {
        const val COMMAND = "peers"
    }

    /**
     * Execute the peers command. Display the peers of a user.
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

        val me = service.nodeInfo().legalIdentities.first().name
        fun isNotary(nodeInfo: NodeInfo) = service.notaryIdentities().any { nodeInfo.isLegalIdentity(it) }
        fun isMe(nodeInfo: NodeInfo) = nodeInfo.legalIdentities.first().name == me
        fun isNetworkMap(nodeInfo : NodeInfo) = nodeInfo.legalIdentities.single().name.organisation == "Network Map Service"

        return service.networkMapSnapshot()
                .filter { isNotary(it).not() && isMe(it).not() && isNetworkMap(it).not() }
                .map { it.legalIdentities.first().name.toString() }
                .listIterator()
    }

    override fun name(): String {
        return COMMAND
    }

    override fun help(): List<String> {
        return listOf("usage: peers <user>")
    }
}