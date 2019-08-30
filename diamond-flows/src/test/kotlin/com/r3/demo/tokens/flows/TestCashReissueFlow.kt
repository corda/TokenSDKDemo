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

class TestCashReissueFlow {
    private lateinit var mockNet: MockNetwork
    private lateinit var nodeIssuer: StartedMockNode
    private lateinit var nodeReceiver: StartedMockNode

    @Before
    fun start() {
        mockNet = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                TestCordapp.findCordapp("com.r3.demo.tokens.contract"),
                TestCordapp.findCordapp("com.r3.demo.tokens.flows")
        )))
        nodeIssuer = mockNet.createNode()
        nodeReceiver = mockNet.createNode()

        listOf(nodeIssuer, nodeReceiver).forEach { it.registerInitiatedFlow(CashIssueFlow.CashIssueFlowResponse::class.java) }
        listOf(nodeIssuer, nodeReceiver).forEach { it.registerInitiatedFlow(CashReissueFlow.CashReissueFlowResponse::class.java) }

        mockNet.startNodes()
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `reissue cash`() {
        val issueAmount = Amount(100, FiatCurrency.getInstance("USD"))
        val reissueAmount = Amount(80, FiatCurrency.getInstance("USD"))
        val holder = nodeReceiver.info.legalIdentities.first()
        val issuer = nodeIssuer.info.legalIdentities.first()

        val issueFuture = nodeIssuer.startFlow(CashIssueFlow(holder, issueAmount))

        mockNet.runNetwork()

        issueFuture.getOrThrow()

        val reissueFuture = nodeReceiver.startFlow(CashReissueFlow(issuer, reissueAmount))

        mockNet.runNetwork()

        val reissueTx = reissueFuture.getOrThrow()

        for (node in listOf(nodeIssuer, nodeReceiver)) {
            val recordedTx = node.services.validatedTransactions.getTransaction(reissueTx.id)
            val txOutputs = recordedTx!!.tx.outputs
            assert(txOutputs.size == 1)

            val recordedState = txOutputs[0].data as FungibleToken

            assertEquals(80, recordedState.amount.quantity)

            assertEquals(recordedState.issuedTokenType.tokenType.tokenIdentifier, "USD")
            assertEquals(recordedState.holder, nodeReceiver.info.legalIdentities.first())
            assertEquals(recordedState.issuer, nodeIssuer.info.legalIdentities.first())
        }
     }
}