package com.r3.demo.tokens.flows

import com.r3.corda.lib.accounts.workflows.services.KeyManagementBackedAccountService
import com.r3.corda.lib.tokens.money.USD
import org.junit.After
import org.junit.Before
import org.junit.Test
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.*
import org.assertj.core.api.Assertions.assertThatExceptionOfType

class TestPurchaseDiamondGradingReportFlow {
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
    fun `create report across nodes with change`() {
        val report = createReport(mockNet, nodeIssuer, nodeDealer)

        val dealerAccountService = nodeDealer.services.cordaService(KeyManagementBackedAccountService::class.java)
        val buyerAccountService = nodeBuyer.services.cordaService(KeyManagementBackedAccountService::class.java)
        val dealer = createAccount(mockNet, dealerAccountService,"Dealer")
        val alice = createAccount(mockNet, buyerAccountService,"Alice")

        issueCash(mockNet, nodeIssuer, alice, 100.USD)

        val purchaseFuture = nodeDealer.startFlow(PurchaseDiamondGradingReportFlow(report.linearId, dealer.state.data, alice.state.data, 60.USD))

        mockNet.runNetwork()

        purchaseFuture.getOrThrow()

        verifyAccountWallet(nodeBuyer, alice, 1, 40)
        verifyAccountWallet(nodeDealer, dealer, 1, 60)
        verifyDiamondTokenPresent(nodeBuyer, alice)
    }

    @Test
    fun `create report across nodes with with two coins`() {
        val report = createReport(mockNet, nodeIssuer, nodeDealer)

        val dealerAccountService = nodeDealer.services.cordaService(KeyManagementBackedAccountService::class.java)
        val buyerAccountService = nodeBuyer.services.cordaService(KeyManagementBackedAccountService::class.java)
        val dealer = createAccount(mockNet, dealerAccountService,"Dealer")
        val alice = createAccount(mockNet, buyerAccountService,"Alice")

        issueCash(mockNet, nodeIssuer, alice, 100.USD)
        issueCash(mockNet, nodeIssuer, alice, 90.USD)

        val purchaseFuture = nodeDealer.startFlow(PurchaseDiamondGradingReportFlow(report.linearId, dealer.state.data, alice.state.data, 160.USD))

        mockNet.runNetwork()

        purchaseFuture.getOrThrow()

        verifyAccountWallet(nodeBuyer, alice, 1, 30)
        verifyAccountWallet(nodeDealer, dealer, 1, 160)
        verifyDiamondTokenPresent(nodeBuyer, alice)
    }

    @Test
    fun `create report within node with change`() {
        val report = createReport(mockNet, nodeIssuer, nodeDealer)

        val accountService = nodeDealer.services.cordaService(KeyManagementBackedAccountService::class.java)
        val dealer = createAccount(mockNet, accountService,"Dealer")
        val alice = createAccount(mockNet, accountService,"Alice")

        issueCash(mockNet, nodeIssuer, alice, 100.USD)

        val purchaseFuture = nodeDealer.startFlow(PurchaseDiamondGradingReportFlow(report.linearId, dealer.state.data, alice.state.data, 60.USD))

        mockNet.runNetwork()

        purchaseFuture.getOrThrow()

        verifyAccountWallet(nodeDealer, alice, 1, 40)
        verifyAccountWallet(nodeDealer, dealer, 1, 60)
        verifyDiamondTokenPresent(nodeDealer, alice)
    }

    @Test
    fun `create report within node with two coins`() {
        val report = createReport(mockNet, nodeIssuer, nodeDealer)

        val accountService = nodeDealer.services.cordaService(KeyManagementBackedAccountService::class.java)
        val dealer = createAccount(mockNet, accountService,"Dealer")
        val alice = createAccount(mockNet, accountService,"Alice")

        issueCash(mockNet, nodeIssuer, alice, 100.USD)
        issueCash(mockNet, nodeIssuer, alice, 90.USD)

        val purchaseFuture = nodeDealer.startFlow(PurchaseDiamondGradingReportFlow(report.linearId, dealer.state.data, alice.state.data, 160.USD))

        mockNet.runNetwork()

        purchaseFuture.getOrThrow()

        verifyAccountWallet(nodeDealer, alice, 1, 30)
        verifyAccountWallet(nodeDealer, dealer, 1, 160)
        verifyDiamondTokenPresent(nodeDealer, alice)
    }

    @Test
    fun `double sell report fail`() {
        val report = createReport(mockNet, nodeIssuer, nodeDealer)

        val accountService = nodeDealer.services.cordaService(KeyManagementBackedAccountService::class.java)
        val dealer = createAccount(mockNet, accountService,"Dealer")
        val alice = createAccount(mockNet, accountService,"Alice")
        val bob = createAccount(mockNet, accountService,"Bob")

        issueCash(mockNet, nodeIssuer, alice, 100.USD)
        issueCash(mockNet, nodeIssuer, bob, 100.USD)

        val aliceFuture = nodeDealer.startFlow(PurchaseDiamondGradingReportFlow(report.linearId, dealer.state.data, alice.state.data, 60.USD))

        mockNet.runNetwork()

        aliceFuture.getOrThrow()

        assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
            val bobFuture = nodeDealer.startFlow(PurchaseDiamondGradingReportFlow(report.linearId, dealer.state.data, bob.state.data, 60.USD))

            mockNet.runNetwork()

            bobFuture.getOrThrow()
        }
    }

    @Test
    fun `double sell report pass`() {
        val report = createReport(mockNet, nodeIssuer, nodeDealer)

        val accountService = nodeDealer.services.cordaService(KeyManagementBackedAccountService::class.java)
        val dealer = createAccount(mockNet, accountService,"Dealer")
        val alice = createAccount(mockNet, accountService,"Alice")
        val bob = createAccount(mockNet, accountService,"Bob")

        issueCash(mockNet, nodeIssuer, alice, 100.USD)
        issueCash(mockNet, nodeIssuer, bob, 100.USD)

        val aliceFuture = nodeDealer.startFlow(PurchaseDiamondGradingReportFlow(report.linearId, dealer.state.data, alice.state.data, 60.USD))

        mockNet.runNetwork()

        val tx = aliceFuture.getOrThrow()
        val tokenId = retrieveDiamondTokenId(nodeDealer, tx)
        val redeemFuture = nodeDealer.startFlow(RedeemDiamondGradingReportFlow(tokenId, alice.state.data, dealer.state.data, 50.USD))

        mockNet.runNetwork()

        val rx = redeemFuture.getOrThrow()

        val bobFuture = nodeDealer.startFlow(PurchaseDiamondGradingReportFlow(report.linearId, dealer.state.data, bob.state.data, 60.USD))

        mockNet.runNetwork()

        bobFuture.getOrThrow()
    }

}