package com.r3.vaultrecycler.explorer

import net.corda.core.crypto.SecureHash
import java.io.PrintWriter

/**
 * Prints the heap usage building the graph.
 */
class HeapExplorer : Explorer {
    override fun explore(
        graph: Map<SecureHash, TransactionNode>,
        attachments: Set<SecureHash>,
        writer: PrintWriter
    ) {
        val runtime = Runtime.getRuntime()

        // It is well known that gc is only a hint
        runtime.gc()

        val maxMemory = runtime.maxMemory()
        val freeMemory = runtime.freeMemory()
        val totalMemory = runtime.totalMemory()
        val usedMemory = totalMemory - freeMemory

        val vaultSize = graph.size
        val percentageUsed = (100 * usedMemory) / maxMemory

        writer.println("Memory: Count=${vaultSize}, Vault size=${vaultSize}, Heap=${percentageUsed}%")
        writer.flush()
    }
}