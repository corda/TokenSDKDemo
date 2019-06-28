package com.r3.vaultrecycler.explorer

import net.corda.core.crypto.SecureHash
import java.io.PrintWriter

/**
 * Primary interface for exploring the Vault
 */
interface Explorer {
    /**
     * Explore the transaction graph and record findings on the writer.
     * A general assumption is that the recycler explorer has already
     * been executed on the graph, so nodes marked for recycling will have
     * TransactionNode.active set to false.
     */
    fun explore(graph: Map<SecureHash, TransactionNode>, attachments: Set<SecureHash>, writer: PrintWriter)

    /**
     * The companion object is used to manage dynamic configuration of the [Explorer] implementations.
     * Each property key can be set globally in the System environment by using the [GLOBAL] prefix,
     * or just for a single running using the [TEMP] prefix.
     */
    companion object {
        const val TEMP = "prefix"
        const val GLOBAL = "vaultrecycler"

        /**
         * Allows an [Explorer] to retrieve a boolean property.
         * Properties can be set in System properties using the [TEMP] or [GLOBAL]
         * prefixes. [TEMP] properties will be cleared at the end of the exploration.
         */
        fun isProperty(key: String): Boolean {
            return try {
                System.getProperty(TEMP + "." + key, System.getProperty(GLOBAL + "." + key, "false")).toBoolean()
            } catch (e: Exception){
                false
            }
        }

        /**
         * Allows an [Explorer] to retrieve a long property.
         * Properties can be set in System properties using the [TEMP] or [GLOBAL]
         * prefixes. [TEMP] properties will be cleared at the end of the exploration.
         */
        fun getProperty(key: String, def: Long): Long {
            return try {
                System.getProperty(TEMP + "." + key, System.getProperty(GLOBAL + "." + key, def.toString())).toLong()
            } catch (e: Exception) {
                def
            }
        }

        /**
         * Allows an [Explorer] to retrieve a string property.
         * Properties can be set in System properties using the [TEMP] or [GLOBAL]
         * prefixes. [TEMP] properties will be cleared at the end of the exploration.
         */
        fun getProperty(key: String, def: String): String {
            return try {
                System.getProperty(TEMP + "." + key, System.getProperty(GLOBAL + "." + key, def))
            } catch (e: Exception) {
                ""
            }
        }
    }
}