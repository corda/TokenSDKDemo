package com.r3.demo.nodes

import com.r3.demo.nodes.commands.*
import net.corda.client.rpc.CordaRPCConnection
import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.RPCConnection
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.NetworkHostAndPort
import org.apache.commons.jexl3.MapContext
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

    while (iterator.hasNext()){
        val argument = iterator.next()
        when (argument) {
            "-d" -> configRoot = iterator.next()
            "-f" -> filename = iterator.next()
            "-s" -> silent = true
            "-h" -> usage()
            else -> throw IllegalArgumentException("Unknown option: ${argument}")
        }
    }

    val main = Main(configRoot)

    if (main.userMap.isEmpty()){
        System.err.println("No users found in ${if (configRoot == ".") "current directory" else configRoot}")
        usage()
    }

    if (!silent){
        println(main.userMap)
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

        val response = main.parseCommand(command, array, line)

        if (!silent){
            response.forEach { System.out.println(it) }
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

    private val legalNamePattern = Pattern.compile("myLegalName=\"([^\"]+)\"")
    private val usernamePattern = Pattern.compile("username=(\\w+)")
    private val passwordPattern = Pattern.compile("password=(\\w+)")
    private val addressPattern = Pattern.compile("address=\"([^\"]+)\"")

    private val connectionMap = HashMap<String, CordaRPCConnection>()
    private val nodeMap = HashMap<String, UniqueIdentifier>()

    val userMap = readConfiguration(configRoot)
    val commandMap = createCommandMap()
    val context = createExecutionContext()

    /**
     * Retrive the [Command] implementation for the command and execute it.
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
     * Map the user name to the [User] object
     */
    fun getUser(name: String): User {
        return userMap[name] ?: throw IllegalArgumentException("Unknown user ${name}")
    }

    /**
     * Return the RPC connection for the [User] object
     */
    fun getConnection(user: User): RPCConnection<CordaRPCOps> {
        if (!connectionMap.containsKey(user.name)){
            val nodeAddress = NetworkHostAndPort.parse(user.address)
            val client = CordaRPCClient(nodeAddress)
            val connection = client.start(user.username, user.password)

            connectionMap[user.name] = connection
        }
        return connectionMap[user.name] ?: throw IllegalArgumentException("Unknown connection ${user.name}")
    }

    /**
     * Translate the user name to the X500 name. The user name is obtained from the
     * name of the directory containing the node.conf file, while the X500 name is based
     * on the legal name.
     */
    fun getWellKnownUser(user: User, service: CordaRPCOps): Party {
        return service.wellKnownPartyFromX500Name(CordaX500Name.parse(user.legalName)) ?: throw IllegalArgumentException("Unknown party name ${user.name}.")
    }

    /**
     * Record the linear id of a state so that it can be referred to by name
     */
    fun registerNode(name:String, linearId: UniqueIdentifier){
        nodeMap[name] = linearId
    }

    /**
     * Retrieve the linear id of a state
     */
    fun retrieveNode(name: String): UniqueIdentifier?{
        if (nodeMap.containsKey(name)){
            return nodeMap[name]
        }

        return nodeMap.entries.first { it.key.startsWith(name) }.value
    }

    /**
     * Create JEXL context for executing scripts.
     * All users are added to the context so that they can be reference directly.
     */
    private fun createExecutionContext() : MapContext {
        val context = MapContext()

        context.set("system", System.out)

        userMap.forEach{
            context.set(it.key, it.value)
        }

        return context
    }

    /**
     * Create a map of string to command implementations
     * for all commands implemented by this application.
     */
    private fun createCommandMap() : Map<String, Command>{
        return mapOf(
            Bye.COMMAND to Bye(),
            Help.COMMAND to Help(),
            Nodes.COMMAND to Nodes(),
            Peers.COMMAND to Peers(),
            Explore.COMMAND to Explore(),
            Whoami.COMMAND to Whoami(),
            IssueCash.COMMAND to IssueCash(),
            PayCash.COMMAND to PayCash(),
            Create.COMMAND to Create(),
            Update.COMMAND to Update(),
            Issue.COMMAND to Issue(),
            Move.COMMAND to Move(),
            Settle.COMMAND to Settle(),
            Purchase.COMMAND to Purchase(),
            Transfer.COMMAND to Transfer(),
            Redeem.COMMAND to Redeem()
        )
    }

    /**
     * Recursively search subdirectories for node.conf configuration file.
     */
    private fun readConfiguration(path: String): Map<String, User> {
        val map = HashMap<String, User>()

        val file = File(path)

        if (!file.exists() || !file.isDirectory){
            logger.error("Cannot find configuration directory ${path}")
        }

        readConfiguration(File(path), map)

        return map.toMap()
    }

    /**
     * Search for node.conf files and recursively search subdirectories.
     */
    private fun readConfiguration(directory: File, map: HashMap<String, User>){
        val file = File(directory, "node.conf")

        if (file.exists()){
            try {
                parseConfiguration(directory.name, file, map)
            } catch (e: Exception){
                logger.warn("Cannot parse configuration for ${directory.name}")
            }
        }

        directory.listFiles{ f -> f.isDirectory}
                .iterator().forEach {
                    readConfiguration(it, map)
                }
    }

    /**
     * Parse a node.conf file to retrieve the legal name, user name,
     * password and node address.
     */
    private fun parseConfiguration(name: String, file: File, map: HashMap<String, User>){
        val encoded = Files.readAllBytes(file.toPath())
        val text = String(encoded, StandardCharsets.UTF_8)
                .replace('\n',' ')
                .replace('\r',' ')
        val legalName = findText(legalNamePattern, text)
        val username = findText(usernamePattern, text)
        val password = findText(passwordPattern, text)
        val address = findText(addressPattern, text)

        map[name] = User(this, name, legalName, username, password, address)
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