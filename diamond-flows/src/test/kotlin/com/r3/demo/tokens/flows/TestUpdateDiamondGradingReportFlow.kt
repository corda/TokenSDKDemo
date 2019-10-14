package com.r3.demo.tokens.flows

import com.r3.corda.lib.accounts.workflows.services.KeyManagementBackedAccountService
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.money.USD
import com.r3.demo.tokens.state.DiamondGradingReport
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.transactions.SignedTransaction
import org.junit.After
import org.junit.Before
import org.junit.Test
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.*

class TestUpdateDiamondGradingReportFlow {
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
    fun `update report same dealer`() {
        val report = createReport(mockNet, nodeIssuer, nodeDealer)

        val accountService = nodeDealer.services.cordaService(KeyManagementBackedAccountService::class.java)
        val dealerState = createAccount(mockNet, accountService, "Dealer")
        val aliceState = createAccount(mockNet, accountService, "Alice")

        issueCash(mockNet, nodeIssuer, aliceState, 100.USD)

        val purchaseFuture = nodeDealer.startFlow(PurchaseDiamondGradingReportFlow(report.linearId, dealerState.state.data, aliceState.state.data, 60.USD))

        mockNet.runNetwork()


        purchaseFuture.getOrThrow()
        val update = report.copy(clarity = DiamondGradingReport.ClarityScale.VS1)
        val updateFuture = nodeIssuer.startFlow(UpdateDiamondGradingReportFlow(report.linearId, update))

        mockNet.runNetwork()

        updateFuture.getOrThrow()

        verifyGradingReport(nodeDealer, update)
    }

    @Test
    fun `update report after transfer same dealer`() {
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

        verifyGradingReport(nodeDealer, report)

        val update = report.copy(color = DiamondGradingReport.ColorScale.G)
        val updateFuture = nodeIssuer.startFlow(UpdateDiamondGradingReportFlow(report.linearId, update))

        mockNet.runNetwork()

        updateFuture.getOrThrow()

        verifyGradingReport(nodeDealer, update)
    }

    @Test
    fun `update report after transfer new dealer`() {
        val report = createReport(mockNet, nodeIssuer, nodeDealer)

        val dealerService = nodeDealer.services.cordaService(KeyManagementBackedAccountService::class.java)
        val buyerService = nodeBuyer.services.cordaService(KeyManagementBackedAccountService::class.java)
        val dealerState = createAccount(mockNet, dealerService, "Dealer")
        val aliceState = createAccount(mockNet, dealerService, "Alice")
        val bobState = createAccount(mockNet, buyerService, "Bob")

        issueCash(mockNet, nodeIssuer, aliceState, 100.USD)
        issueCash(mockNet, nodeIssuer, bobState, 100.USD)

        val purchaseFuture = nodeDealer.startFlow(PurchaseDiamondGradingReportFlow(report.linearId, dealerState.state.data, aliceState.state.data, 60.USD))

        mockNet.runNetwork()

        val reportTx = purchaseFuture.getOrThrow()

        verifyGradingReportMissing(nodeBuyer)

        val tokenId = retrieveDiamondTokenId(nodeDealer, reportTx)

        val transferFuture = nodeDealer.startFlow(TransferDiamondGradingReportFlow(tokenId, aliceState.state.data, bobState.state.data, 50.USD))

        mockNet.runNetwork()

        transferFuture.getOrThrow()

        verifyGradingReport(nodeBuyer, report)

        val update = report.copy(requester = nodeBuyer.info.legalIdentities.first(),  color = DiamondGradingReport.ColorScale.G)
        val updateFuture = nodeIssuer.startFlow(UpdateDiamondGradingReportFlow(report.linearId, update))

        mockNet.runNetwork()

        updateFuture.getOrThrow()

        verifyGradingReport(nodeBuyer, update)
    }

    @Test
    fun `update report transfer across nodes`() {
        val report = createReport(mockNet, nodeIssuer, nodeDealer)

        val dealerService = nodeDealer.services.cordaService(KeyManagementBackedAccountService::class.java)
        val buyerService = nodeBuyer.services.cordaService(KeyManagementBackedAccountService::class.java)
        val dealerState = createAccount(mockNet, dealerService, "Dealer")
        val aliceState = createAccount(mockNet, dealerService, "Alice")
        val bobState = createAccount(mockNet, buyerService, "Bob")

        issueCash(mockNet, nodeIssuer, aliceState, 100.USD)
        issueCash(mockNet, nodeIssuer, bobState, 100.USD)

        val purchaseFuture = nodeDealer.startFlow(PurchaseDiamondGradingReportFlow(report.linearId, dealerState.state.data, aliceState.state.data, 60.USD))

        mockNet.runNetwork()

        val reportTx = purchaseFuture.getOrThrow()

        verifyGradingReportMissing(nodeBuyer)

        val tokenId = retrieveDiamondTokenId(nodeDealer, reportTx)

        val transferFuture = nodeDealer.startFlow(TransferDiamondGradingReportFlow(tokenId, aliceState.state.data, bobState.state.data, 50.USD))

        mockNet.runNetwork()

        transferFuture.getOrThrow()

        verifyGradingReport(nodeBuyer, report)

        val update = report.copy(color = DiamondGradingReport.ColorScale.G)
        val updateFuture = nodeIssuer.startFlow(UpdateDiamondGradingReportFlow(report.linearId, update))

        mockNet.runNetwork()

        updateFuture.getOrThrow()

        verifyGradingReport(nodeBuyer, update)
    }

    private fun retrieveDiamondTokenId(node: StartedMockNode, tx: SignedTransaction): UniqueIdentifier {
        val recordedTx = node.services.validatedTransactions.getTransaction(tx.id)
        val txOutputs = recordedTx!!.tx.outputs

        return txOutputs.map { it.data }.filterIsInstance(NonFungibleToken::class.java).map{ it.linearId }.first()
    }
}