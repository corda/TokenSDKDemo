package com.r3.demo.tokens.flows

import com.r3.corda.lib.accounts.workflows.services.KeyManagementBackedAccountService
import com.r3.corda.lib.tokens.money.USD
import net.corda.core.utilities.contextLogger
import org.junit.After
import org.junit.Before
import org.junit.Test
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.*

class TestCashTransferFlow {
    private lateinit var mockNet: MockNetwork
    private lateinit var nodeIssuer: StartedMockNode
    private lateinit var nodePayer: StartedMockNode
    private lateinit var nodeBuyer: StartedMockNode

    @Before
    fun start() {
        mockNet = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                TestCordapp.findCordapp("com.r3.corda.lib.accounts.contracts"),
                TestCordapp.findCordapp("com.r3.corda.lib.accounts.workflows"),
                TestCordapp.findCordapp("com.r3.demo.tokens.contract"),
                TestCordapp.findCordapp("com.r3.demo.tokens.flows")
        )))
        nodeIssuer = mockNet.createNode()
        nodePayer = mockNet.createNode()
        nodeBuyer = mockNet.createNode()

        listOf(nodeIssuer, nodePayer, nodeBuyer).forEach {
            it.registerInitiatedFlow(CashIssueFlow.CashIssueFlowResponse::class.java)
        }

        mockNet.startNodes()
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `transfer cash within node`() {
        val accountService = nodePayer.services.cordaService(KeyManagementBackedAccountService::class.java)

        val aliceState = createAccount(mockNet, accountService, "Alice")
        val bobState = createAccount(mockNet, accountService,"Bob")

        issueCash(mockNet, nodeIssuer, aliceState, 100.USD)

        val transferFuture = nodePayer.startFlow(CashTransferFlow(aliceState.state.data, bobState.state.data, 40.USD))

        mockNet.runNetwork()

        transferFuture.getOrThrow()

        verifyAccountWallet(nodePayer, aliceState, 1,60)
        verifyAccountWallet(nodePayer, bobState, 1,40)
    }

    @Test
    fun `transfer cash within node two coins`() {
        val accountService = nodePayer.services.cordaService(KeyManagementBackedAccountService::class.java)

        val aliceState = createAccount(mockNet, accountService, "Alice")
        val bobState = createAccount(mockNet, accountService,"Bob")

        issueCash(mockNet, nodeIssuer, aliceState, 100.USD)
        issueCash(mockNet, nodeIssuer, aliceState, 80.USD)

        val transferFuture = nodePayer.startFlow(CashTransferFlow(aliceState.state.data, bobState.state.data, 120.USD))

        mockNet.runNetwork()

        transferFuture.getOrThrow()

        verifyAccountWallet(nodePayer, aliceState, 1,60)
        verifyAccountWallet(nodePayer, bobState, 1,120)
    }

    @Test
    fun `transfer cash across nodes`() {
        val holderAccountService = nodePayer.services.cordaService(KeyManagementBackedAccountService::class.java)
        val buyerAccountService = nodeBuyer.services.cordaService(KeyManagementBackedAccountService::class.java)

        val aliceState = createAccount(mockNet, holderAccountService, "Alice")
        val bobState = createAccount(mockNet, buyerAccountService,"Bob")

        issueCash(mockNet, nodeIssuer, aliceState, 100.USD)

        val transferFuture = nodePayer.startFlow(CashTransferFlow(aliceState.state.data, bobState.state.data, 40.USD))

        mockNet.runNetwork()

        transferFuture.getOrThrow()

        verifyAccountWallet(nodePayer, aliceState, 1,60)
        verifyAccountWallet(nodeBuyer, bobState, 1,40)
    }

    @Test
    fun `transfer cash across nodes two coins`() {
        val holderAccountService = nodePayer.services.cordaService(KeyManagementBackedAccountService::class.java)
        val buyerAccountService = nodeBuyer.services.cordaService(KeyManagementBackedAccountService::class.java)

        val aliceState = createAccount(mockNet, holderAccountService, "Alice")
        val bobState = createAccount(mockNet, buyerAccountService,"Bob")

        issueCash(mockNet, nodeIssuer, aliceState, 100.USD)
        issueCash(mockNet, nodeIssuer, aliceState, 80.USD)

        val transferFuture = nodePayer.startFlow(CashTransferFlow(aliceState.state.data, bobState.state.data, 120.USD))

        mockNet.runNetwork()

        transferFuture.getOrThrow()

        verifyAccountWallet(nodePayer, aliceState, 1,60)
        verifyAccountWallet(nodeBuyer, bobState, 1,120)
    }

    @Test
    fun `transfer cash across nodes over spend`() {
        val holderAccountService = nodePayer.services.cordaService(KeyManagementBackedAccountService::class.java)
        val buyerAccountService = nodeBuyer.services.cordaService(KeyManagementBackedAccountService::class.java)

        val aliceState = createAccount(mockNet, holderAccountService, "Alice")
        val bobState = createAccount(mockNet, buyerAccountService,"Bob")

        issueCash(mockNet, nodeIssuer, aliceState, 80.USD)

        try {
            val transferFuture = nodePayer.startFlow(CashTransferFlow(aliceState.state.data, bobState.state.data, 120.USD))

            mockNet.runNetwork()

            transferFuture.getOrThrow()
        } catch (e: Throwable) {
            // com.r3.corda.lib.tokens.selection.InsufficientBalanceException
        }

        verifyAccountWallet(nodePayer, aliceState, 1,80)
        verifyAccountWallet(nodeBuyer, bobState, 0,0)
    }
}