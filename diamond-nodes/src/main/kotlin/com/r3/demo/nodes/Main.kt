package com.r3.demo.nodes

import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.demo.nodes.commands.*
import net.corda.client.rpc.CordaRPCConnection
import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.RPCConnection
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.NetworkHostAndPort
import org.slf4j.LoggerFactory
import java.io.*
import java.lang.Exception
import java.lang.IllegalArgumentException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.*
import java.util.regex.Pattern

/**
 * Entry point for the Vault Generator command line tool.
 * The process should be taken in a directory where the node.conf
 * files can be found, otherwise the [-d] option should be used
 * to point to the configuration directory.
 */
fun main(args: Array<String>) {
    val iterator = args.iterator()
    var configRoot = "."
    var filename = ""
    var silent = false
    var logTime = false

    while (iterator.hasNext()){
        val argument = iterator.next()
        when (argument) {
            "-d" -> configRoot = iterator.next()
            "-f" -> filename = iterator.next()
            "-s" -> silent = true
            "-l" -> logTime = true
            "-h" -> usage()
            else -> throw IllegalArgumentException("Unknown option: ${argument}")
        }
    }

    val main = Main(configRoot)

    if (main.nodeMap.isEmpty()){
        System.err.println("No nodes found in ${if (configRoot == ".") "current directory" else configRoot}")
        usage()
    }

    if (!silent){
        println(main.nodeMap)
    }

    val reader = createInputStream(filename)

    while (true) {
        if (filename.isBlank()){
            System.out.print("> ")
        }

        val line = reader.readLine() ?: break
        val array = line.trim().split(Pattern.compile("\\s+"))
        val command = array[0].toLowerCase()

        if (command.isBlank()){
            continue
        }

        if (logTime){
            System.out.println("Time start ${Date()}")
        }

        val response = main.parseCommand(command, array, line)

        if (!silent){
            response.forEach { System.out.println(it) }
            if (logTime && Utilities.logtime >= 0){
                System.out.println("Time duration = ${Utilities.logtime}ms")
                Utilities.logCancel()
            }
        }
    }

    reader.close()
}

/**
 * Return a [BufferedReader] for the input stream. If a file was given on the command
 * line then read from the file, otherwise use standard input.
 */
private fun createInputStream(filename: String): BufferedReader {
    return if (filename.isBlank()){
        BufferedReader(InputStreamReader(System.`in`))
    } else {
        BufferedReader(FileReader(filename))
    }
}

/**
 * Display usage and exit.
 */
private fun usage(){
    System.err.println("usage: Main [-d config-directory] [-f file] [-s]")
    System.exit(1)
}

/**
 * Manages the interaction between the command line and the RPC connections.
 * On construction the configRoot directory and subdirectories will be scanned
 * for node.conf files to establish the pool of users/nodes. Subsequently commands
 * can be processed using the [parseCommand] method.
 */
class Main(configRoot: String) {
    private val logger = LoggerFactory.getLogger("Main")

    private val legalNamePattern = Pattern.compile("myLegalName\"?[\\s=:]+\"([^\"]+)\"")
    private val usernamePattern = Pattern.compile("\\s\"?username\"?[\\s=:]+\"?(\\w+)")
    private val passwordPattern = Pattern.compile("\\s\"?password\"?[\\s=:]+\"?(\\w+)")
    private val addressPattern = Pattern.compile("address\"?[\\s=:]+\"([^\"]+)\"")

    private val connectionMap = HashMap<String, CordaRPCConnection>()
    private val accountMap = HashMap<String, AccountInfo>()
    private val stateMap = HashMap<String, UniqueIdentifier>()

    val nodeMap = readConfiguration(configRoot)
    val commandMap = createCommandMap()

    /**
     * Retrieve the [Command] implementation for the command and execute it.
     */
    fun parseCommand(command: String, array: List<String>, line: String): Iterator<String> {
        val processor = commandMap[command] ?: return listOf("Unknown command ${command}. Type help for help").listIterator()

        return try {
            processor.execute(this, array, line)
        } catch (e: Exception){
            (listOf("Error processing commands ${command}. ${e.message}") + processor.help()).listIterator()
        }
    }

    /**
     * Map the name to the [Node] object
     */
    fun retrieveNode(name: String): Node {
        return nodeMap[name.toLowerCase()] ?: throw IllegalArgumentException("Unknown node ${name}")
    }

    /**
     * Register a [Node] to a legal name
     */
    fun registerNode(name: AccountInfo, node: Node){
        nodeMap[name.host.name.toString()] = node
    }

    /**
     * Map the name to the [Node] object
     */
    fun retrieveNode(name: AccountInfo): Node {
        return nodeMap[name.host.name.toString()] ?: throw IllegalArgumentException("Unknown node ${name.name}")
    }

    /**
     * Return the RPC connection for the [Node] object
     */
    fun getConnection(node: Node): RPCConnection<CordaRPCOps> {
        if (!connectionMap.containsKey(node.name)){
            val nodeAddress = NetworkHostAndPort.parse(node.address)
            val client = CordaRPCClient(nodeAddress)
            val connection = client.start(node.username, node.password)

            connectionMap[node.name] = connection
        }
        return connectionMap[node.name] ?: throw IllegalArgumentException("Unknown connection ${node.name}")
    }

    /**
     * Translate the node name to the X500 name. The node name is obtained from the
     * name of the directory containing the node.conf file, while the X500 name is based
     * on the legal name.
     */
    fun getWellKnownUser(node: Node, service: CordaRPCOps): Party {
        return service.wellKnownPartyFromX500Name(CordaX500Name.parse(node.legalName)) ?: throw IllegalArgumentException("Unknown party name ${node.name}.")
    }

    /**
     * Record the buyer info for an buyer name
     */
    fun registerAccount(name:String, account: AccountInfo) {
        accountMap[name.toLowerCase()] = account
    }

    /**
     * Retrieve the buyer info of an buyer
     */
    fun retrieveAccount(name: String): AccountInfo {
        return accountMap[name.toLowerCase()] ?: throw IllegalArgumentException("Unknown account ${name}")
    }

    /**
     * Record the linear id of a state so that it can be referred to by name
     */
    fun registerState(name:String, linearId: UniqueIdentifier){
        stateMap[name] = linearId
    }

    /**
     * Retrieve the linear id of a state
     */
    fun retrieveState(name: String): UniqueIdentifier?{
        if (stateMap.containsKey(name)){
            return stateMap[name]
        }

        return stateMap.entries.first { it.key.startsWith(name) }.value
    }

    /**
     * Create a map of string to command implementations
     * for all commands implemented by this application.
     */
    private fun createCommandMap() : Map<String, Command>{
        return mapOf(
            Bye.COMMAND to Bye(),
            Help.COMMAND to Help(),
            ListNode.COMMAND to ListNode(),
            ListAccount.COMMAND to ListAccount(),
            IssueCash.COMMAND to IssueCash(),
            ReissueCash.COMMAND to ReissueCash(),
            PayCash.COMMAND to PayCash(),
            LoadAccounts.COMMAND to LoadAccounts(),
            CreateAccount.COMMAND to CreateAccount(),
            CreateReport.COMMAND to CreateReport(),
            UpdateReport.COMMAND to UpdateReport(),
            PurchaseDiamond.COMMAND to PurchaseDiamond(),
            TransferDiamond.COMMAND to TransferDiamond(),
            RedeemDiamond.COMMAND to RedeemDiamond()
        )
    }

    /**
     * Recursively search subdirectories for node.conf configuration file.
     */
    private fun readConfiguration(path: String): MutableMap<String, Node> {
        val map = HashMap<String, Node>()

        val file = File(path)

        if (!file.exists() || !file.isDirectory){
            logger.error("Cannot find configuration directory ${path}")
        }

        readConfiguration(File(path), map)

        return map
    }

    /**
     * Search for node.conf files and recursively search subdirectories.
     */
    private fun readConfiguration(directory: File, map: HashMap<String, Node>){
        val file = File(directory, "node.conf")

        if (file.exists()){
            try {
                parseConfiguration(directory.name, file, map)
            } catch (e: Exception){
                logger.warn("Cannot parse configuration for ${directory.name}")
            }
        } else {
            directory.listFiles { f -> f.isDirectory }
                    .iterator().forEach {
                        readConfiguration(it, map)
                    }
        }
    }

    /**
     * Parse a node.conf file to retrieve the legal name, user name,
     * password and node address.
     */
    private fun parseConfiguration(name: String, file: File, map: HashMap<String, Node>){
        val encoded = Files.readAllBytes(file.toPath())
        val text = String(encoded, StandardCharsets.UTF_8)
                .replace('\n',' ')
                .replace('\r',' ')
        val legalName = findText(legalNamePattern, text)
        val username = findText(usernamePattern, text)
        val password = findText(passwordPattern, text)
        val address = findText(addressPattern, text)

        if (!legalName.contains("notary", true)){
            map[name.toLowerCase()] = Node(this, name, legalName, username, password, address)
        }
    }

    /**
     * Retrieve the value described by the pattern from the text parameter.
     * If the pattern is not found then throw an [IllegalArgumentException]
     */
    private fun findText(pattern: Pattern, text: String): String {
        val matcher = pattern.matcher(text)
        if (matcher.find()){
            return matcher.group(1)
        }
        throw IllegalArgumentException()
    }
}