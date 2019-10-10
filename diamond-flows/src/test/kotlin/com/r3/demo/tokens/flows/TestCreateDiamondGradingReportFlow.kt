package com.r3.demo.tokens.flows

import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.services.KeyManagementBackedAccountService
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.money.FiatCurrency
import com.r3.demo.tokens.state.DiamondGradingReport
import org.junit.After
import org.junit.Before
import org.junit.Test
import net.corda.core.contracts.Amount
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.*
import java.math.BigDecimal
import kotlin.test.assertEquals

class TestCreateDiamondGradingReportFlow {
    private lateinit var mockNet: MockNetwork
    private lateinit var nodeIssuer: StartedMockNode
    private lateinit var nodeDealer: StartedMockNode

    @Before
    fun start() {
        mockNet = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows"),
                TestCordapp.findCordapp("com.r3.corda.lib.accounts.contracts"),
                TestCordapp.findCordapp("com.r3.corda.lib.accounts.workflows"),
                TestCordapp.findCordapp("com.r3.demo.tokens.contract"),
                TestCordapp.findCordapp("com.r3.demo.tokens.flows")
        )))
        nodeIssuer = mockNet.createNode()
        nodeDealer = mockNet.createNode()

        listOf(nodeIssuer, nodeDealer).forEach {
            it.registerInitiatedFlow(CashIssueFlow.CashIssueFlowResponse::class.java)
        }

        mockNet.startNodes()
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `create report`() {
        val issuer = nodeIssuer.info.legalIdentities.first()
        val dealer = nodeDealer.info.legalIdentities.first()

        val report = DiamondGradingReport("1.0", DiamondGradingReport.ColorScale.D, DiamondGradingReport.ClarityScale.VI1, "oval", issuer, dealer)

        val issueFuture = nodeIssuer.startFlow(CreateDiamondGradingReportFlow(report))

        mockNet.runNetwork()

        issueFuture.getOrThrow()

        verifyGradingReport(nodeDealer, report)
        verifyGradingReport(nodeIssuer, report)
    }
}