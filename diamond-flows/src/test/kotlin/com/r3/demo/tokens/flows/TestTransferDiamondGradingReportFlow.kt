package com.r3.demo.tokens.flows

import com.r3.corda.lib.accounts.workflows.services.KeyManagementBackedAccountService
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.money.USD
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.transactions.SignedTransaction
import org.junit.After
import org.junit.Before
import org.junit.Test
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.*
import org.assertj.core.api.Assertions

class TestTransferDiamondGradingReportFlow {
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
    fun `transfer report within nodes`() {
        val report = createReport(mockNet, nodeIssuer, nodeDealer)

        val accountService = nodeDealer.services.cordaService(KeyManagementBackedAccountService::class.java)
        val dealerState = createAccount(mockNet, accountService, "Dealer")
        val aliceState = createAccount(mockNet, accountService, "Alice")
        val bobState = createAccount(mockNet, accountService, "Bob")

        issueCash(mockNet, nodeIssuer, aliceState, 100.USD)
        issueCash(mockNet, nodeIssuer, bobState, 100.USD)

        val purchaseFuture = nodeDealer.startFlow(PurchaseDiamondGradingReportFlow(report.linearId, dealerState.state.data, aliceState.state.data, 60.USD))

        mockNet.runNetwork()

        val reportTx = purchaseFuture.getOrThrow()

        val tokenId = retrieveDiamondTokenId(nodeDealer, reportTx)

        val transferFuture = nodeDealer.startFlow(TransferDiamondGradingReportFlow(tokenId, aliceState.state.data, bobState.state.data, 50.USD))

        mockNet.runNetwork()

        transferFuture.getOrThrow()

        verifyAccountWallet(nodeDealer, aliceState, 0, 90)
        verifyAccountWallet(nodeDealer, bobState, 1, 50)
        verifyDiamondTokenMissing(nodeDealer, aliceState)
        verifyDiamondTokenPresent(nodeDealer, bobState)
    }

    @Test
    fun `transfer report within nodes illegal sale`() {
        val report = createReport(mockNet, nodeIssuer, nodeDealer)

        val accountService = nodeDealer.services.cordaService(KeyManagementBackedAccountService::class.java)

        val dealerState = createAccount(mockNet, accountService, "Dealer")
        val aliceState = createAccount(mockNet, accountService, "Alice")
        val charlieState = createAccount(mockNet, accountService, "Charlie")
        val bobState = createAccount(mockNet, accountService, "Bob")

        issueCash(mockNet, nodeIssuer, aliceState, 100.USD)
        issueCash(mockNet, nodeIssuer, charlieState, 100.USD)
        issueCash(mockNet, nodeIssuer, bobState, 100.USD)

        val purchaseFuture = nodeDealer.startFlow(PurchaseDiamondGradingReportFlow(report.linearId, dealerState.state.data, aliceState.state.data, 60.USD))

        mockNet.runNetwork()

        val reportTx = purchaseFuture.getOrThrow()

        val tokenId = retrieveDiamondTokenId(nodeDealer, reportTx)

        Assertions.assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
            val transferFuture = nodeDealer.startFlow(TransferDiamondGradingReportFlow(tokenId, charlieState.state.data, bobState.state.data, 50.USD))

            mockNet.runNetwork()

            transferFuture.getOrThrow()
        }
    }

    @Test
    fun `transfer report within nodes outside dealer`() {
        val report = createReport(mockNet, nodeIssuer, nodeDealer)

        val dealerService = nodeDealer.services.cordaService(KeyManagementBackedAccountService::class.java)
        val accountService = nodeBuyer.services.cordaService(KeyManagementBackedAccountService::class.java)
        val dealerState = createAccount(mockNet, dealerService, "Dealer")
        val aliceState = createAccount(mockNet, accountService, "Alice")
        val bobState = createAccount(mockNet, accountService, "Bob")

        issueCash(mockNet, nodeIssuer, aliceState, 100.USD)
        issueCash(mockNet, nodeIssuer, bobState, 100.USD)

        val purchaseFuture = nodeDealer.startFlow(PurchaseDiamondGradingReportFlow(report.linearId, dealerState.state.data, aliceState.state.data, 60.USD))

        mockNet.runNetwork()

        val reportTx = purchaseFuture.getOrThrow()

        val tokenId = retrieveDiamondTokenId(nodeDealer, reportTx)

        val transferFuture = nodeBuyer.startFlow(TransferDiamondGradingReportFlow(tokenId, aliceState.state.data, bobState.state.data, 50.USD))

        mockNet.runNetwork()

        transferFuture.getOrThrow()

        verifyAccountWallet(nodeBuyer, aliceState, 0, 90)
        verifyAccountWallet(nodeBuyer, bobState, 1, 50)
        verifyDiamondTokenMissing(nodeBuyer, aliceState)
        verifyDiamondTokenPresent(nodeBuyer, bobState)
    }

    @Test
    fun `transfer report across nodes no change`() {
        val report = createReport(mockNet, nodeIssuer, nodeDealer)

        val dealerAccountService = nodeDealer.services.cordaService(KeyManagementBackedAccountService::class.java)
        val buyerAccountService = nodeBuyer.services.cordaService(KeyManagementBackedAccountService::class.java)

        val dealerState = createAccount(mockNet, dealerAccountService, "Dealer")
        val aliceState = createAccount(mockNet, dealerAccountService, "Alice")
        val bobState = createAccount(mockNet, buyerAccountService, "Bob")

        issueCash(mockNet, nodeIssuer, aliceState, 100.USD)
        issueCash(mockNet, nodeIssuer, bobState, 100.USD)

        val purchaseFuture = nodeDealer.startFlow(PurchaseDiamondGradingReportFlow(report.linearId, dealerState.state.data, aliceState.state.data, 60.USD))

        mockNet.runNetwork()

        val reportTx = purchaseFuture.getOrThrow()

        val tokenId = retrieveDiamondTokenId(nodeDealer, reportTx)

        val transferFuture = nodeDealer.startFlow(TransferDiamondGradingReportFlow(tokenId, aliceState.state.data, bobState.state.data, 100.USD))

        mockNet.runNetwork()

        transferFuture.getOrThrow()

        verifyAccountWallet(nodeDealer, aliceState, 0, 140)
        verifyAccountWallet(nodeBuyer, bobState, 0, 0)
        verifyDiamondTokenMissing(nodeDealer, aliceState)
        verifyDiamondTokenPresent(nodeBuyer, bobState)
    }

    @Test
    fun `transfer report across nodes with change`() {
        val report = createReport(mockNet, nodeIssuer, nodeDealer)

        val dealerAccountService = nodeDealer.services.cordaService(KeyManagementBackedAccountService::class.java)
        val buyerAccountService = nodeBuyer.services.cordaService(KeyManagementBackedAccountService::class.java)

        val dealerState = createAccount(mockNet, dealerAccountService, "Dealer")
        val aliceState = createAccount(mockNet, dealerAccountService, "Alice")
        val bobState = createAccount(mockNet, buyerAccountService, "Bob")

        issueCash(mockNet, nodeIssuer, aliceState, 100.USD)
        issueCash(mockNet, nodeIssuer, bobState, 100.USD)

        val purchaseFuture = nodeDealer.startFlow(PurchaseDiamondGradingReportFlow(report.linearId, dealerState.state.data, aliceState.state.data, 60.USD))

        mockNet.runNetwork()

        val reportTx = purchaseFuture.getOrThrow()

        val tokenId = retrieveDiamondTokenId(nodeDealer, reportTx)

        val transferFuture = nodeDealer.startFlow(TransferDiamondGradingReportFlow(tokenId, aliceState.state.data, bobState.state.data, 50.USD))

        mockNet.runNetwork()

        transferFuture.getOrThrow()

        verifyAccountWallet(nodeDealer, aliceState, 0, 90)
        verifyAccountWallet(nodeBuyer, bobState, 1, 50)
        verifyDiamondTokenMissing(nodeDealer, aliceState)
        verifyDiamondTokenPresent(nodeBuyer, bobState)
    }

    @Test
    fun `transfer report across nodes illegal sale`() {
        val report = createReport(mockNet, nodeIssuer, nodeDealer)

        val dealerAccountService = nodeDealer.services.cordaService(KeyManagementBackedAccountService::class.java)
        val buyerAccountService = nodeBuyer.services.cordaService(KeyManagementBackedAccountService::class.java)

        val dealerState = createAccount(mockNet, dealerAccountService, "Dealer")
        val aliceState = createAccount(mockNet, dealerAccountService, "Alice")
        val charlieState = createAccount(mockNet, dealerAccountService, "Charlie")
        val bobState = createAccount(mockNet, buyerAccountService, "Bob")

        issueCash(mockNet, nodeIssuer, aliceState, 100.USD)
        issueCash(mockNet, nodeIssuer, charlieState, 100.USD)
        issueCash(mockNet, nodeIssuer, bobState, 100.USD)

        val purchaseFuture = nodeDealer.startFlow(PurchaseDiamondGradingReportFlow(report.linearId, dealerState.state.data, aliceState.state.data, 60.USD))

        mockNet.runNetwork()

        val reportTx = purchaseFuture.getOrThrow()

        val tokenId = retrieveDiamondTokenId(nodeDealer, reportTx)

        Assertions.assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
            val transferFuture = nodeDealer.startFlow(TransferDiamondGradingReportFlow(tokenId, charlieState.state.data, bobState.state.data, 50.USD))

            mockNet.runNetwork()

            transferFuture.getOrThrow()
        }
    }

    private fun retrieveDiamondTokenId(node: StartedMockNode, tx: SignedTransaction): UniqueIdentifier {
        val recordedTx = node.services.validatedTransactions.getTransaction(tx.id)
        val txOutputs = recordedTx!!.tx.outputs

        return txOutputs.map { it.data }.filterIsInstance(NonFungibleToken::class.java).map{ it.linearId }.first()
    }
}