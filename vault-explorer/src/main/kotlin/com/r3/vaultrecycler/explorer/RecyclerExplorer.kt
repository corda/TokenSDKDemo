package com.r3.vaultrecycler.explorer

import net.corda.core.crypto.SecureHash
import java.io.PrintWriter
import java.util.*
import kotlin.collections.HashSet

/**
 * Explores the transaction graph and labels nodes either active or inactive
 * depending on if the node is part of an active subgraph or can be recycled.
 * <P>
 * This explorer should be executed first since other explorers use the active
 * status in their analysis. The explorer also removes attachment IDs from the
 * attachments set which are linked to active transactions.
 * <P>
 * The [Explorer] property [AGGRESSIVE] can be used to activate the aggressive
 * detector that recycles nodes with no relevance to the vault's owner.
 */
class RecyclerExplorer : Explorer {
    companion object {
        const val AGGRESSIVE = "aggressive"
    }

    /**
     * Indicates whether to run the aggressive recycler.
     */
    var aggressive = Explorer.isProperty(AGGRESSIVE)

    override fun explore(
        graph: Map<SecureHash, TransactionNode>,
        attachments: Set<SecureHash>,
        writer: PrintWriter
    ) {
        graph.values.onEach { it.queued = false }

        val queue = LinkedList<TransactionNode>()

        // Create a queue consisting of the active nodes, and walk the tree
        // marking any connected node as active
        queue.addAll( graph.values.filter{ !it.isFullyConsumed(aggressive) }.onEach { it.queued = true } )

        //val total = graph.size
        //val activeCount = queue.size

        val activeAttachments = HashSet<SecureHash>()

        while (queue.isNotEmpty()){
            val node = queue.pop()
            node.active = true
            node.inputs.map{ it.txhash }.plus(node.outputs).plus(node.references).forEach {
                val item = graph[it]
                if (item != null && !item.queued){
                    item.queued = true
                    queue.add(item)
                }
            }
            activeAttachments.addAll(node.attachments)
        }

        if (attachments is MutableSet){
            attachments.removeAll(activeAttachments)
        }

        //val recycledCount = graph.values.count{ !it.active }
        //val recycledAttachments = attachments.size

        //writer.println("Transaction count: ${total}")
        //writer.println("Transactions in this vault with UTXOs: ${activeCount}")
        //writer.println("Transactions that can be recycled: ${recycledCount}")
        //writer.println("Attachments that can be recycled: ${recycledAttachments}")
        writer.flush()
    }
}