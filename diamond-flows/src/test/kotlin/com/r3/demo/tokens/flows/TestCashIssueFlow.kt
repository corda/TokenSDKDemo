package com.r3.demo.tokens.flows

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.money.FiatCurrency
import io.github.classgraph.ClassGraph
import org.junit.After
import org.junit.Before
import org.junit.Test
import net.corda.core.contracts.Amount
import net.corda.core.internal.pooledScan
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.*
import net.corda.testing.node.internal.TestCordappImpl
import net.corda.testing.node.internal.TestCordappInternal
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals

class TestCashIssueFlow {
    private lateinit var mockNet: MockNetwork
    private lateinit var nodeIssuer: StartedMockNode
    private lateinit var nodeReceiver: StartedMockNode

    @Before
    fun start() {
        System.getProperty("java.class.path").split(";").filter { it.contains("demo")}.forEach { println(it) }
        println("JarPath = " + (TestCordapp.findCordapp("com.r3.demo.tokens.flows") as TestCordappImpl).jarFile)
        mockNet = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                TestCordapp.findCordapp("com.r3.demo.tokens.contract"),
                TestCordapp.findCordapp("com.r3.demo.tokens.flows")
//                DummyCordappImpl(File("../build/libs/tokens-contracts-1.1-SNAPSHOT.jar").toPath()),
//                DummyCordappImpl(File("../build/libs/diamond-contracts-0.1.jar").toPath()),
//                DummyCordappImpl(File("./build/libs/diamond-flows-0.1.jar").toPath())
        )))
        nodeIssuer = mockNet.createNode()
        nodeReceiver = mockNet.createNode()

        listOf(nodeIssuer, nodeReceiver).forEach { it.registerInitiatedFlow(CashIssueFlow.CashIssueFlowResponse::class.java) }

        mockNet.startNodes()
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `issue cash`() {
        val amount = Amount(100, FiatCurrency.getInstance("USD"))
        val holder = nodeReceiver.info.legalIdentities.first()
        val future = nodeIssuer.startFlow(CashIssueFlow(holder, amount))

        mockNet.runNetwork()

        val signedTx = future.getOrThrow()

        for (node in listOf(nodeIssuer, nodeReceiver)) {
            val recordedTx = node.services.validatedTransactions.getTransaction(signedTx.id)
            val txOutputs = recordedTx!!.tx.outputs
            assert(txOutputs.size == 1)

            val recordedState = txOutputs[0].data as FungibleToken

            assertEquals(recordedState.amount.quantity, 100)
            assertEquals(recordedState.issuedTokenType.tokenType.tokenIdentifier, "USD")
            assertEquals(recordedState.holder, nodeReceiver.info.legalIdentities.first())
            assertEquals(recordedState.issuer, nodeIssuer.info.legalIdentities.first())
        }
//        println(System.getProperty("java.class.path"))
//        println(findRootPaths("com.r3.corda.lib.tokens.contracts"))
     }

    private fun findRootPaths(scanPackage: String): Set<Path> {
        val set =
            ClassGraph().whitelistPackages(scanPackage)
                    .pooledScan()
                    .use { it.allResources }
                    .asSequence()
                    .map { it.classpathElementFile.toPath() }
//                    .filterNot { it.toString().endsWith("-tests.jar") }
//                    .map { if (it.toString().endsWith(".jar")) it else TestCordappImpl.findProjectRoot(it) }
                    .toSet()
        return set
    }
    data class DummyCordappImpl(override val jarFile: Path) : TestCordappInternal() {
        override val config: Map<String, Any> get() = emptyMap()
        override fun withConfig(config: Map<String, Any>): DummyCordappImpl = copy()
        override fun withOnlyJarContents(): DummyCordappImpl = copy()
    }

}