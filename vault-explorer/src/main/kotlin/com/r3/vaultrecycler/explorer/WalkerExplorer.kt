package com.r3.vaultrecycler.explorer

import net.corda.core.crypto.SecureHash
import java.io.PrintWriter
import java.util.*

/**
 * Walk the transaction graph to count the number
 * of transaction sub-graphs. The [RecyclerExplorer]
 * must have been executed first to mark active/consumed
 * nodes.
 */
class WalkerExplorer : Explorer {
    override fun explore(
        graph: Map<SecureHash, TransactionNode>,
        attachments: Set<SecureHash>,
        writer: PrintWriter
    ) {
        val outerQueue = LinkedList<TransactionNode>()
        val innerQueue = LinkedList<TransactionNode>()
        val subGraphs = LinkedList<TransactionNode>()

        outerQueue.addAll( graph.values.onEach { it.queued = false } )

        // Run through the nodes. When a new node is found then
        // run an inner loop to mark its sub graph.
        while (outerQueue.isNotEmpty()){
            val node = outerQueue.pop()
            if (!node.queued) {
                subGraphs.add(node)
                innerQueue.add(node)
                while (innerQueue.isNotEmpty()) {
                    val innerNode = innerQueue.pop()
                    innerNode.queued = true
                    innerNode.inputs.map{ it.txhash }.plus(innerNode.outputs).plus(innerNode.references).forEach {
                        val item = graph[it]
                        if (item != null && !item.queued) {
                            item.queued = true
                            innerQueue.add(item)
                        }
                    }
                }
            }
        }

        // A representative from each sub graph is recorded
        // to obtain the final counts
        val activeCount = subGraphs.count{ it.active }

        writer.println("Number of active subgraphs: ${activeCount}")
        writer.println("Number of consumed subgraphs: ${subGraphs.size - activeCount}")
        writer.flush()
    }
}