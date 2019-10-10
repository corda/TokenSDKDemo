package com.r3.demo.tokens.flows

import com.r3.corda.lib.accounts.workflows.services.KeyManagementBackedAccountService
import com.r3.corda.lib.tokens.money.USD
import org.junit.After
import org.junit.Before
import org.junit.Test
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.*

class TestRedeemDiamondGradingReportFlow {
    private lateinit var mockNet: MockNetwork
    private lateinit var nodeIssuer: StartedMockNode
    private lateinit var nodeDealer: StartedMockNode
    private lateinit var nodeBuyer: StartedMockNode

    @Before
    fun start() {
        mockNet = MockNetwork(MockNetworkParameters(
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4),
                cordappsForAllNodes = listOf(
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows"),
                TestCordapp.findCordapp("com.r3.corda.lib.accounts.contracts"),
                TestCordapp.findCordapp("com.r3.corda.lib.accounts.workflows"),
                TestCordapp.findCordapp("com.r3.corda.lib.ci.workflows"),
                TestCordapp.findCordapp("com.r3.demo.tokens.contract"),
                TestCordapp.findCordapp("com.r3.demo.tokens.flows")
        )))
        nodeIssuer = mockNet.createNode()
        nodeDealer = mockNet.createNode()
        nodeBuyer = mockNet.createNode()

        listOf(nodeIssuer, nodeDealer, nodeBuyer).forEach {
            it.registerInitiatedFlow(CashIssueFlow.CashIssueFlowResponse::class.java)
        }

        mockNet.startNodes()
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `redeem token within nodes`() {
        val report = createReport(mockNet, nodeIssuer, nodeDealer)

        val accountService = nodeDealer.services.cordaService(KeyManagementBackedAccountService::class.java)
        val dealerState = createAccount(mockNet, accountService, "Dealer")
        val aliceState = createAccount(mockNet, accountService, "Alice")

        issueCash(mockNet, nodeIssuer, aliceState, 100.USD)

        val purchaseFuture = nodeDealer.startFlow(PurchaseDiamondGradingReportFlow(report.linearId, dealerState.state.data, aliceState.state.data, 60.USD))

        mockNet.runNetwork()

        val reportTx = purchaseFuture.getOrThrow()

        verifyDiamondTokenPresent(nodeDealer, aliceState)

        val tokenId = retrieveDiamondTokenId(nodeDealer, reportTx)

        val redeemFuture = nodeDealer.startFlow(RedeemDiamondGradingReportFlow(tokenId, aliceState.state.data, dealerState.state.data, 50.USD))

        mockNet.runNetwork()

        redeemFuture.getOrThrow()

        verifyAccountWallet(nodeDealer, aliceState, 0, 90)
        verifyAccountWallet(nodeDealer, dealerState, 1, 10)
        verifyDiamondTokenMissing(nodeDealer, aliceState)
    }

    @Test
    fun `redeem report across nodes`() {
        val report = createReport(mockNet, nodeIssuer, nodeDealer)

        val dealerAccountService = nodeDealer.services.cordaService(KeyManagementBackedAccountService::class.java)
        val buyerAccountService = nodeBuyer.services.cordaService(KeyManagementBackedAccountService::class.java)

        val dealerState = createAccount(mockNet, dealerAccountService, "Dealer")
        val aliceState = createAccount(mockNet, buyerAccountService, "Alice")

        issueCash(mockNet, nodeIssuer, aliceState, 100.USD)

        val purchaseFuture = nodeDealer.startFlow(PurchaseDiamondGradingReportFlow(report.linearId, dealerState.state.data, aliceState.state.data, 60.USD))

        mockNet.runNetwork()

        val reportTx = purchaseFuture.getOrThrow()

        verifyDiamondTokenPresent(nodeBuyer, aliceState)

        val tokenId = retrieveDiamondTokenId(nodeBuyer, reportTx)

        val transferFuture = nodeBuyer.startFlow(RedeemDiamondGradingReportFlow(tokenId, aliceState.state.data, dealerState.state.data, 50.USD))

        mockNet.runNetwork()

        transferFuture.getOrThrow()

        verifyAccountWallet(nodeDealer, dealerState, 1, 10)
        verifyAccountWallet(nodeBuyer, aliceState, 0, 90)
        verifyDiamondTokenMissing(nodeDealer, aliceState)
    }
}