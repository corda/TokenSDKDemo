package com.r3.vaultrecycler.explorer

import net.corda.core.contracts.ContractState
import net.corda.core.crypto.SecureHash
import java.io.PrintWriter
import java.util.*

/**
 * Walk the transaction graph to count the classes used as output states.
 * The [RecyclerExplorer] must have been executed first to mark active/consumed nodes.
 */
class ClassExplorer : Explorer {
    override fun explore(
        graph: Map<SecureHash, TransactionNode>,
        attachments: Set<SecureHash>,
        writer: PrintWriter
    ) {
        val map = HashMap<Class<ContractState>, ClassCount>()

        graph.values.forEach {
            for (index in 0 until it.outputStates.size){
                val clazz = it.outputStates[index]
                val counter = map.getOrPut(clazz){ ClassCount() }
                when {
                    !it.isFullyConsumed() -> {
                        when {
                            it.isOutputStateConsumed(index) -> counter.activeTransactionConsumed++
                            it.isOutputParticipant(index) -> counter.activeTransactionUnconsumed++
                            else -> counter.activeTransactionNonParticipant++
                        }
                    }
                    it.active -> counter.consumedTransaction++
                    else -> counter.recycledTransaction++
                }
            }
        }
        writer.println("Class statistics")
        map.forEach{
            writer.println("  ${it.key.simpleName} active non participant: ${it.value.activeTransactionNonParticipant}")
            writer.println("  ${it.key.simpleName} active unconsumed: ${it.value.activeTransactionUnconsumed}")
            writer.println("  ${it.key.simpleName} active consumed: ${it.value.activeTransactionConsumed}")
            writer.println("  ${it.key.simpleName} consumed transaction: ${it.value.consumedTransaction}")
            writer.println("  ${it.key.simpleName} recycled transaction: ${it.value.recycledTransaction}")
        }
        writer.flush()
    }

    /**
     * Records the activity counts for a state class
     */
    private data class ClassCount(
        /**
         * Count of unconsumed states (in active transactions)
         */
        var activeTransactionUnconsumed: Int = 0,

        /**
         * Count of non participant unconsumed states (in active transactions)
         */
        var activeTransactionNonParticipant: Int = 0,

        /**
         * Count of consumed states (in active transactions)
         */
        var activeTransactionConsumed: Int = 0,

        /**
         * Count of consumed states (in consumed transactions)
         */
        var consumedTransaction: Int = 0,

        /**
         * Count of consumed states (in recyclable transactions)
         */
        var recycledTransaction: Int = 0)

}