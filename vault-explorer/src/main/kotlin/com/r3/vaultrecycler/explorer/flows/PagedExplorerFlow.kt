package com.r3.vaultrecycler.explorer.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.vaultrecycler.explorer.*
import com.r3.vaultrecycler.explorer.impl.BasicExplorer
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.deserialize
import net.corda.core.transactions.SignedTransaction
import java.io.*
import java.lang.StringBuilder
import java.sql.Blob
import java.util.*
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Runs the explorer algorithm using a paged query of the vault.
 * The flow returns a zipped byte array of text output from the
 * explorer implementations.
 * <P>
 * The [explorers] parameter is a comma separated list of [Explorer]
 * implementations and key=value pairs. If the explorer implementation
 * is in the [PACKAGE] package then only the simple class name is needed.
 * All key/value pairs are set in the System properties environment and
 * can be accessed by explorer implementations. See [Explorer] interface.
 */
@InitiatingFlow
@StartableByRPC
class PagedExplorerFlow(val filename: String,
                        val pageSize: Int,
                        val explorers: String?) : FlowLogic<ByteArray>() {

    @Suppress("UNUSED")
    constructor(filename: String) :
            this(filename, 0, "")

    @Suppress("UNUSED")
    constructor(filename: String, explorers: String) :
            this(filename, 0, explorers)

    @Suppress("UNUSED")
    constructor(filename: String, pageSize: Int) :
            this(filename, pageSize, "")

    companion object {
        const val DEF_PAGE_SIZE = 10
        const val PACKAGE = "com.r3.vaultrecycler.explorer"
    }

    @Suspendable
    override fun call(): ByteArray {
        val list = retrieveTransactionIds()
        val classes = retrieveClassAttachments()
        val size = list.size

        logger.info("Retrieved ${size} elements")

        val iterator = TransactionIterator(list)
        val file = createFile(filename)
        val stream = FileOutputStream(file)
        val writer = PrintWriter(stream)

        try {
            // The explorers parameter can contain a comma separated list
            // of system properties to set
            explorers?.let{ configureEnvironment(explorers, writer) }

            val explorer = BasicExplorer(ourIdentity, iterator, classes, size, writer)

            explorer.verifyHeapSpace()

            val pair = explorer.scanVault()

            RecyclerExplorer().explore(pair.first, pair.second, writer)
            //WalkerExplorer().explore(pair.first, pair.second, writer)
            //ClassExplorer().explore(pair.first, pair.second, writer)
            PrintVault().explore(pair.first, pair.second, writer)

            // The explorers parameter can contain a comma separated list
            // of additional Explorer implementations to execute
            explorers?.let{ executeExplorers(explorers, pair.first, pair.second, writer) }

            // Reset any properties set on the command line so they won't
            // effect future executions
            explorers?.let{ clearEnvironment(explorers) }
        } finally {
            writer.close()
        }

        val output = ByteArrayOutputStream()

        ZipOutputStream(output).use{
            saveFileToZipStream(file, it)
        }

        return output.toByteArray()
    }

    /**
     * Checks for key=value tags in the explorers list
     */
    private fun configureEnvironment(explorers: String, writer: PrintWriter){
        explorers.split(Pattern.compile("[,:;]"))
            .filter{ it.contains('=')}
            .forEach {
                val array = it.split('=')
                if (array.size == 2 && array[0].isNotBlank() && array[1].isNotBlank()){
                    writer.println("Setting system property ${array[0]} to ${array[1]}")
                    System.setProperty(Explorer.TEMP + "." + array[0], array[1])
                }
            }
    }

    /**
     * Checks for key=value tags in the explorers list
     */
    private fun clearEnvironment(explorers: String){
        explorers.split(Pattern.compile("[,:;]"))
            .filter{ it.contains('=')}
            .forEach {
                val array = it.split('=')
                if (array.size == 2 && array[0].isNotBlank() && array[1].isNotBlank()){
                    System.clearProperty(Explorer.TEMP + "." + array[0])
                }
            }
    }

    /**
     * Checks for [Explorer] tags in the explorers list
     */
    private fun executeExplorers(explorers: String,
                                 graph: Map<SecureHash, TransactionNode>,
                                 classes: Set<SecureHash>,
                                 writer: PrintWriter){
        explorers.split(Pattern.compile("[,:;]"))
            .filter{ it.isNotBlank() }
            .filter{ !it.contains('=') }
            .forEach {
                executeExplorer(it.trim(), graph, classes, writer)
            }
    }

    /**
     * Executes an optional explorer on the transaction graph.
     * If the explorer does not include a package name then use
     * the default package for explorers.
     */
    private fun executeExplorer(explorer: String,
                                graph: Map<SecureHash, TransactionNode>,
                                classes: Set<SecureHash>,
                                writer: PrintWriter){
        try {
            @Suppress("UNCHECKED_CAST")
            val clazz = if (explorer.contains('.')) {
                Class.forName(explorer)
            } else {
                Class.forName(PACKAGE + "." + explorer)
            } as Class<Explorer>

            clazz.newInstance().explore(graph, classes, writer)
        } catch (e: Exception){
            logger.error("Cannot instigate Explorer ${explorer}")
        }
    }

    /**
     * Returns a file reference to the file name in the tmp directory
     */
    private fun createFile(filename: String): File {
        val parent = File("tmp")
        parent.mkdir()
        return File(parent, File(filename).name)
    }

    /**
     * Saves the text file to the zip stream
     */
    private fun saveFileToZipStream(file: File, zip: ZipOutputStream) {
        val origin = BufferedInputStream(FileInputStream(file))
        val entry = ZipEntry(file.name)

        zip.putNextEntry(entry)
        origin.copyTo(zip, 1024)
        zip.closeEntry()
    }

    /**
     * Read in all tx ids from the database.
     */
    private fun retrieveTransactionIds(): ArrayList<String>{
        val list = ArrayList<String>()
        val query = "select TX_ID from NODE_TRANSACTIONS"
        val session = serviceHub.jdbcSession()

        session.prepareStatement(query).use {
            it.executeQuery().use {
                while (it.next()) {
                    list.add(it.getString(1))
                }
            }
        }
        return list
    }

    /**
     * Read in all class att ids from the database.
     */
    private fun retrieveClassAttachments(): Set<SecureHash>{
        val list = HashSet<SecureHash>()
        val query = "select ATT_ID, CONTRACT_CLASS_NAME from NODE_ATTACHMENTS_CONTRACTS"
        val session = serviceHub.jdbcSession()

        session.prepareStatement(query).use {
            it.executeQuery().use {
                while (it.next()){
                    val hash = SecureHash.parse(it.getString(1))
                    list.add(hash)
                }
            }
        }
        return list
    }

    /**
     * Implements an iterator that can retrieve the transaction blobs
     * in pages. If the current result set is empty then the next
     * page is loaded until all transactions have been retrieved.
     */
    inner class TransactionIterator(val secureHashList: ArrayList<String>) : Iterator<SignedTransaction>{
        private var resultList = readTransactions()

        override fun hasNext(): Boolean {
            if (resultList.isNotEmpty()){
                return true
            }

            if (secureHashList.isEmpty()){
                return false
            }

            resultList = readTransactions()

            return resultList.isNotEmpty()
        }

        override fun next(): SignedTransaction {
            return resultList.pop().deserialize(context = SerializationDefaults.STORAGE_CONTEXT)
        }

        /**
         * Reads the next page of transactions
         */
        private fun readTransactions() : LinkedList<Blob> {
            val list = LinkedList<Blob>()
            val query = constructQuery(secureHashList)
            val session = serviceHub.jdbcSession()

            session.prepareStatement(query).use {
                it.executeQuery().use {
                    while (it.next()){
                        list.add(it.getBlob(1))
                    }
                }
            }
            return list
        }

        /**
         * Constructs a query from the list of tx ids up to the
         * page size.
         */
        private fun constructQuery(list: ArrayList<String>): String {
            if (list.isEmpty()){
                return "select TRANSACTION_VALUE from NODE_TRANSACTIONS where 1 = 2"
            }

            val builder = StringBuilder("select TRANSACTION_VALUE from NODE_TRANSACTIONS where TX_ID in (")
            val count = getPageSize(list.size)

            builder.append('\'').append(list[0]).append('\'')
            for (i in 1 until count) {
                builder.append(',').append('\'').append(list[i]).append('\'')
            }
            builder.append(')')

            list.subList(0, count).clear()

            return builder.toString()
        }

        private fun getPageSize(size: Int): Int {
            return Math.min(size, if (pageSize < 1) DEF_PAGE_SIZE else pageSize)
        }
    }
}

/**
 * Runs the explorer algorithm using a paged query of the vault
 */
fun CordaRPCOps.runPagedExplorer(filename: String, pageSize: Int = 0, explorers: String = ""): ByteArray {
    return this.startFlow(::PagedExplorerFlow, filename, pageSize, explorers ).returnValue.get()
}