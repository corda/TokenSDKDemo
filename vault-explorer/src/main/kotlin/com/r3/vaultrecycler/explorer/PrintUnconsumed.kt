package com.r3.vaultrecycler.explorer

import net.corda.core.crypto.SecureHash
import java.io.PrintWriter

/**
 * Prints the UTXOs in a transaction graph.
 * The [RecyclerExplorer] must have been executed first to mark active/consumed nodes.
 */
class PrintUnconsumed : Explorer {
    companion object {
        const val AGGRESSIVE = RecyclerExplorer.AGGRESSIVE
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
        graph.values.filter { !it.isFullyConsumed(aggressive) }.onEach { displayNode(it, writer) }

        writer.flush()
    }

    private fun displayNode(node: TransactionNode, writer: PrintWriter) {
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
    }

    private fun nodeStatus(node: TransactionNode): String {
        return when {
            !node.isFullyConsumed(aggressive) -> "active"
            node.active-> "consumed"
            else -> "recycled"
        }
    }
}