package com.r3.vaultrecycler.explorer

import net.corda.core.crypto.SecureHash
import java.io.PrintWriter
import java.util.*

/**
 * Prints the transaction graph.
 * The [RecyclerExplorer] must have been executed first to mark active/consumed nodes.
 */
class PrintVault : Explorer {
    companion object {
        const val AGGRESSIVE = RecyclerExplorer.AGGRESSIVE
        const val TXID = "txid"
    }

    /**
     * Indicates whether to run the aggressive recycler.
     */
    var aggressive = Explorer.isProperty(AGGRESSIVE)

    /**
     * Indicates whether to display only a subgraph
     */
    var txid = Explorer.getProperty(TXID, "")

    override fun explore(
        graph: Map<SecureHash, TransactionNode>,
        attachments: Set<SecureHash>,
        writer: PrintWriter
    ) {
        if (txid.isBlank()){
            exploreVault(graph, writer)
        } else {
            exploreSubgraph(graph, writer, txid)
        }

        writer.flush()
    }

    /**
     * Traverse the entire vault from roots to leaves
     */
    private fun exploreVault(
        graph: Map<SecureHash, TransactionNode>,
        writer: PrintWriter
    ){
        val outerQueue = LinkedList<TransactionNode>()

        graph.values.onEach { it.queued = false }

        // Walk from the roots down
        outerQueue.addAll( graph.values.filter{ it.isRoot() }.onEach { it.queued = true } )
        walkGraph(graph, writer, outerQueue)

        // Walk across the graph in case there are any missed nodes
        outerQueue.addAll( graph.values.filter{ !it.queued }.onEach { it.queued = true } )
        walkGraph(graph, writer, outerQueue)
    }

    /**
     * Traverse the subgraph containing the transaction id
     */
    private fun exploreSubgraph(
        graph: Map<SecureHash, TransactionNode>,
        writer: PrintWriter,
        txid: String
    ){
        val outerQueue = LinkedList<TransactionNode>()

        graph.values.onEach { it.queued = false }

        // Walk from the given transaction
        outerQueue.addAll( graph.values.filter{ it.txId.toString().startsWith(txid) }.onEach { it.queued = true } )
        walkGraph(graph, writer, outerQueue)
    }

    private fun walkGraph(graph: Map<SecureHash, TransactionNode>,
                          writer: PrintWriter,
                          queue: LinkedList<TransactionNode>) {
        while (queue.isNotEmpty()) {
            val node = queue.pop()
            val name = node.txId.toString().substring(0, 8)
            writer.println("${name} ${nodeStatus(node)}")
            node.inputs.forEach {
                writer.println("  + < ${it.txhash.toString().substring(0, 8)}[${it.index}]")
            }
            node.references.forEach {
                writer.println("  + - ${it.txhash.toString().substring(0, 8)}[${it.index}]")
            }
            for (index in 0 until node.outputStates.size){
                val clazz = node.outputStates[index]
                val status = when {
                    node.isOutputStateConsumed(index) -> "consumed"
                    node.isOutputParticipant(index) -> "unconsumed"
                    else -> "non participant"
                }
                writer.println("  + > ${clazz.simpleName} ${status}")
            }
            node.outputs.forEach {
                val item = graph[it]
                if (item != null && !item.queued){
                    item.queued = true
                    queue.add(item)
                }
            }
        }
    }

    private fun nodeStatus(node: TransactionNode): String {
        return when {
            !node.isFullyConsumed(aggressive) -> "active"
            node.active-> "consumed"
            else -> "recycled"
        }
    }
}