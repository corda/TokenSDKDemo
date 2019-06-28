package com.r3.vaultrecycler.explorer.impl

import com.r3.vaultrecycler.explorer.Explorer
import com.r3.vaultrecycler.explorer.TransactionNode
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import java.io.PrintWriter
import java.util.*

/**
 * Scans the vault to construct an im memory graph of the vault
 */
open class BasicExplorer(private val party: AbstractParty,
                        private val snapshot: Iterator<SignedTransaction>,
                        private val classAttachments: Set<SecureHash>,
                        private val vaultSize: Int,
                        private val writer: PrintWriter
) {
    companion object {
        const val INITIAL_LIMIT = "initial.limit"
        const val CRITICAL_LIMIT = "critical.limit"
        const val HEAP_STATS = "heap.stats"
    }

    /**
     * The maximum percentage of heap memory that can be used before the explorer
     * starts. This value can be set using the System Property
     * <code>vaultrecycler.initial.limit</code>.
     */
    var initialLimit: Long = Explorer.getProperty(INITIAL_LIMIT, 80)

    /**
     * The percentage of heap memory that can be used before the explorer
     * aborts. This value can be set using the System Property
     * <code>vaultrecycler.critical.limit</code>.
     */
    var criticalLimit: Long = Explorer.getProperty(CRITICAL_LIMIT, 90L)

    /**
     * Boolean indicating if extra heap memory logs should be recorded.
     */
    var displayHeapStatistics: Boolean = Explorer.isProperty(HEAP_STATS)

    private val logger = FlowLogic.currentTopLevel?.logger

    /**
     * Scans the vault to construct an im memory graph of the vault
     */
    fun scanVault(): Pair<Map<SecureHash, TransactionNode>, Set<SecureHash>> {
        val counts = Counts(0,0)
        val graph = HashMap<SecureHash, TransactionNode>()
        val nodeAttachments = HashSet<SecureHash>()

        while(snapshot.hasNext()){
            verifyRemainingHeapCapacity(displayHeapStatistics, counts)

            val details = snapshot.next()
            val txId = details.coreTransaction.id
            val inputs = details.inputs.toTypedArray()
            val references = details.references.toTypedArray()
            val attachments = processAttachments(details.tx, classAttachments)
            val outputStates = details.coreTransaction.outputStates.map{ it.javaClass }.toTypedArray()
            val count = details.coreTransaction.outputStates.size

            val node = graph.getOrPut(txId){ TransactionNode(txId) }

            node.inputs = inputs
            node.references = references
            node.attachments = attachments
            node.outputStates = outputStates
            node.outputsCreated = count

            // Mark node as a potentially active node to be queued if not already fully consumed
            node.queued = !node.isFullyConsumed()

            // Update all inputs to this node, create placeholders if necessary
            details.inputs.forEach {
                if (graph.containsKey(it.txhash)){
                    graph[it.txhash]?.recordOutputState(txId, it.index)
                } else {
                    graph[it.txhash] = TransactionNode(it.txhash, txId, it.index)
                }
            }

            details.coreTransaction.outputStates.forEachIndexed { index, it ->
                node.recordOutputParticipant(it.participants.contains(party), index)
            }

            nodeAttachments.addAll(attachments)
        }
        writer.flush()

        return Pair(graph, nodeAttachments)
    }

    /**
     * Runs a heuristic check on the estimate memory required to run
     * the recycler. Throws IllegalArgumentException if current free
     * memory is less than the heuristic estimate.
     *
     * @throws IllegalArgumentException If insufficient memory
     */
    fun verifyHeapSpace() {
        val percentageUsed = estimateRemainingHeapCapacity()

        if (percentageUsed > initialLimit){
            throw IllegalArgumentException("Recycling aborted, memory limit of ${percentageUsed}% exceeded")
        }
    }
    /**
     * Helper class to track the monitoring of heap memory.
     */
    private class Counts (var currentCount: Int, var nextEstimate: Int)

    /**
     * Runs a check on the remaining heap space to verify it is below
     * the critical level. An internal count is kept so that the check
     * is only run at every 10% mark of the vault size.
     */
    private fun verifyRemainingHeapCapacity(displayHeapStatistics: Boolean, counts: Counts) {
        counts.currentCount++

        if (counts.currentCount * 10 / vaultSize <= counts.nextEstimate) {
            return
        }

        counts.nextEstimate++

        val percentageUsed = estimateRemainingHeapCapacity()
        val text = "Memory: Count=${counts.currentCount}, Vault size=${vaultSize}, Heap=${percentageUsed}%"

        logger?.info(text)

        if (displayHeapStatistics){
            writer.println(text)
        }

        if (percentageUsed > criticalLimit){
            throw IllegalArgumentException("Recycling aborted, memory limit of ${percentageUsed}% exceeded")
        }
    }

    /**
     * Returns a percentage estimate of the remaining heap space.
     */
    private fun estimateRemainingHeapCapacity(): Long{
        val runtime = Runtime.getRuntime()

        // It is well known that gc is only a hint
        runtime.gc()

        val maxMemory = runtime.maxMemory()
        val freeMemory = runtime.freeMemory()
        val totalMemory = runtime.totalMemory()
        val usedMemory = totalMemory - freeMemory

        return (100 * usedMemory) / maxMemory
    }

    /**
     * Take the attachment list from the transaction and remove
     * the class attachments
     */
    private fun processAttachments(tx: WireTransaction, classAttachments: Set<SecureHash>): Array<SecureHash>{
        return tx.attachments.filter { !classAttachments.contains(it) }.toTypedArray()
    }
}