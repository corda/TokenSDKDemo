package com.r3.demo.nodes

import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.demo.nodes.commands.*
import com.r3.demo.tokens.flows.RetrieveAccountsFlow
import net.corda.client.rpc.CordaRPCConnection
import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.RPCConnection
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.utilities.NetworkHostAndPort
import org.slf4j.LoggerFactory
import java.io.*
import java.lang.Exception
import java.lang.IllegalArgumentException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.*
import java.util.regex.Pattern
import kotlin.system.exitProcess

/**
 * Entry point for the Diamond command line tool.
 * The process should be taken in a directory where the node.conf
 * files can be found, otherwise the [-d] option should be used
 * to point to the configuration directory.
 */
fun main(args: Array<String>) {
    val iterator = args.iterator()

    while (iterator.hasNext()){
        val argument = iterator.next()
        when (argument) {
            "-d" -> Main.configRoot = iterator.next()
            "-f" -> Main.filename = iterator.next()
            "-l" -> Main.logTime = true
            "-h" -> usage()
            else -> throw IllegalArgumentException("Unknown option: $argument")
        }
    }

    val main = Main()

    if (main.nodeMap.isEmpty()){
        System.err.println("No nodes found in ${if (Main.configRoot == ".") "current directory" else Main.configRoot}")
        usage()
    }

    val reader = createInputStream(Main.filename)

    while (true) {
        if (Main.filename.isBlank()){
            print("> ")
        }

        val line = reader.readLine() ?: break
        val array = line.trim().split(Pattern.compile("\\s+"))
        val command = array[0].toLowerCase()

        if (isBlank(command)){
            continue
        }

        if (Main.logTime){
            println("Time start ${Date()}")
        }

        val response = main.parseCommand(command, array, line)

        response.forEach { println(it) }
        if (Main.logTime && Utilities.logtime >= 0){
            println("Time duration = ${Utilities.logtime}ms")
            Utilities.logCancel()
        }
    }

    reader.close()
}

private fun isBlank(command: String): Boolean {
    return command.isBlank() || command[0] == '#' || command[0].isISOControl()
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
    System.err.println("usage: Main [-d config-directory] [-f file] [-u curator]")

    exitProcess(1)
}

/**
 * Manages the interaction between the command line and the RPC connections.
 * On construction the configRoot directory and subdirectories will be scanned
 * for node.conf files to establish the pool of users/nodes. Subsequently commands
 * can be processed using the [parseCommand] method.
 */
class Main {
    companion object {
        var configRoot = "."
        var filename = ""
        var logTime = false
    }
    private val logger = LoggerFactory.getLogger("Main")

    private val legalNamePattern = Pattern.compile("myLegalName\"?[\\s=:]+\"([^\"]+)\"")
    private val usernamePattern = Pattern.compile("\\s\"?username\"?[\\s=:]+\"?(\\w+)")
    private val passwordPattern = Pattern.compile("\\s\"?password\"?[\\s=:]+\"?(\\w+)")
    private val addressPattern = Pattern.compile("address\"?[\\s=:]+\"([^\"]+)\"")

    private val connectionMap = HashMap<String, CordaRPCConnection>()
    private val stateMap = HashMap<String, UniqueIdentifier>()
    private val accountMap = HashMap<String, AccountInfo>()//readAccounts()

    val nodeMap = readConfiguration()
    val commandMap = createCommandMap()

    /**
     * Retrieve the [Command] implementation for the command and execute it.
     */
    fun parseCommand(command: String, array: List<String>, line: String): Iterator<String> {
        val processor = commandMap[command] ?: return listOf("Unknown command ${command}. Type help for help").listIterator()

        return try {
            processor.execute(this, array, line)
        } catch (e: Exception){
            val text = e.toString()
            when {
                text.contains("InsufficientBalanceException") ->
                    listOf("Insufficient Balance - transaction could not be processed")
                text.contains("Report already in use") ->
                    listOf("Report already in use - transaction could not be processed")
                text.contains("Collection contains no element matching the predicate") ->
                    listOf("Item not found or not owned by account - transaction could not be processed")
                text.contains("State not found") ->
                    listOf("Item not found or not owned by account - transaction could not be processed")
                else -> (listOf("Error processing commands ${command}. ${e.message}") + processor.help())
            }.listIterator()
        }
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
    fun retrieveNode(name: String): Node {
        return nodeMap[name.toLowerCase()] ?: throw IllegalArgumentException("Unknown node $name")
    }

    /**
     * Map the account name to the [Node] object
     */
    fun retrieveNode(account: AccountInfo): Node {
        val name = account.host.name.organisation
        return nodeMap[name.toLowerCase()] ?: throw IllegalArgumentException("Unknown account ${account.name}")
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
     * Map a node object to the party. If the party is not recorded
     * on the node object then execute a RPC to get the party name
     * from the node service.
     */
    fun getWellKnownUser(node: Node): Party {
        if (node.party == null){
            val service = getConnection(node).proxy
            node.party = service.wellKnownPartyFromX500Name(CordaX500Name.parse(node.legalName))
        }
        return node.party ?: throw IllegalArgumentException("Unknown party name ${node.name}.")
    }

    /**
     * Record the account info
     */
    fun registerAccount(name:String, account: AccountInfo) {
        accountMap[name.toLowerCase()] = account
    }

    /**
     * Retrieve the account info from a name
     */
    fun retrieveAccount(name: String): AccountInfo {
        return accountMap[name.toLowerCase()] ?: throw IllegalArgumentException("Unknown account $name")
    }

    /**
     * Retrieve the buyer info of an buyer
     */
    fun hasAccount(name: String): Boolean {
        return accountMap.containsKey(name.toLowerCase())
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
    private fun readConfiguration(): MutableMap<String, Node> {
        val map = HashMap<String, Node>()

        val file = File(configRoot)

        if (!file.exists() || !file.isDirectory){
            logger.error("Cannot find configuration directory $configRoot")
        }

        readConfiguration(File(configRoot), map)
        displayAccounts()

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
                    ?.iterator()?.forEach {
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
            val node = Node(this, name, legalName, username, password, address)
            println("Node ${node.name} on [${node.legalName}]")
            map[name.toLowerCase()] = node
            readAccounts(node)
        }
    }

    private fun readAccounts(node: Node) {
        try {
            if (!node.isDealer()){
                return
            }
            val connection = getConnection(node)
            val service = connection.proxy

            val states = service.startTrackedFlow(::RetrieveAccountsFlow).returnValue.get()

            states.forEach { account ->
                registerAccount(account.name, account)
            }
        } catch (e: Exception){
            println("Cannot read accounts from ${node.name}")
        }
    }

    private fun displayAccounts() {
        accountMap.values.forEach {account ->
            println("Account ${account.name} on [${account.host.name}] with id ${account.identifier.id}")
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