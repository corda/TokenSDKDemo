package com.r3.demo.tokens.flows

import com.r3.corda.lib.accounts.workflows.services.KeyManagementBackedAccountService
import com.r3.corda.lib.tokens.money.USD
import org.junit.After
import org.junit.Before
import org.junit.Test
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.*

class TestCashReissueFlow {
    private lateinit var mockNet: MockNetwork
    private lateinit var nodeIssuer: StartedMockNode
    private lateinit var nodeHolder: StartedMockNode

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
        nodeHolder = mockNet.createNode()

        listOf(nodeIssuer, nodeHolder).forEach {
            it.registerInitiatedFlow(CashIssueFlow.CashIssueFlowResponse::class.java)
        }

        mockNet.startNodes()
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `reissue cash split`() {
        val accountService = nodeHolder.services.cordaService(KeyManagementBackedAccountService::class.java)

        val aliceState = createAccount(mockNet, accountService, "Alice")

        issueCash(mockNet, nodeIssuer, aliceState, 100.USD)

        verifyAccountWallet(nodeHolder, aliceState, 1, 100)

        val reissueFuture = nodeHolder.startFlow(CashReissueFlow(aliceState.state.data, nodeIssuer.info.legalIdentities.first(), 40.USD))

        mockNet.runNetwork()

        reissueFuture.getOrThrow()

        verifyAccountWallet(nodeHolder, aliceState, 2,100)
     }

    @Test
    fun `reissue cash combine`() {
        val accountService = nodeHolder.services.cordaService(KeyManagementBackedAccountService::class.java)

        val aliceState = createAccount(mockNet, accountService, "Alice")

        issueCash(mockNet, nodeIssuer, aliceState, 40.USD)
        issueCash(mockNet, nodeIssuer, aliceState, 60.USD)

        verifyAccountWallet(nodeHolder, aliceState, 2, 100)

        val reissueFuture = nodeHolder.startFlow(CashReissueFlow(aliceState.state.data, nodeIssuer.info.legalIdentities.first(), 100.USD))

        mockNet.runNetwork()

        reissueFuture.getOrThrow()

        verifyAccountWallet(nodeHolder, aliceState, 1,100)
    }

    @Test
    fun `reissue cash combine change`() {
        val accountService = nodeHolder.services.cordaService(KeyManagementBackedAccountService::class.java)

        val aliceState = createAccount(mockNet, accountService, "Alice")

        issueCash(mockNet, nodeIssuer, aliceState, 40.USD)
        issueCash(mockNet, nodeIssuer, aliceState, 40.USD)

        verifyAccountWallet(nodeHolder, aliceState, 2, 80)

        val reissueFuture = nodeHolder.startFlow(CashReissueFlow(aliceState.state.data, nodeIssuer.info.legalIdentities.first(), 60.USD))

        mockNet.runNetwork()

        reissueFuture.getOrThrow()

        verifyAccountWallet(nodeHolder, aliceState, 2,80)
    }
}