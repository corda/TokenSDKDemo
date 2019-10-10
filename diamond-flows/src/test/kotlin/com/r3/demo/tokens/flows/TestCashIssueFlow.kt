package com.r3.demo.tokens.flows

import com.r3.corda.lib.accounts.workflows.services.KeyManagementBackedAccountService
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.money.USD
import org.junit.After
import org.junit.Before
import org.junit.Test
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.*
import kotlin.test.assertEquals

class TestCashIssueFlow {
    private lateinit var mockNet: MockNetwork
    private lateinit var nodeIssuer: StartedMockNode
    private lateinit var nodeReceiver: StartedMockNode

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
        val accountService = nodeReceiver.services.cordaService(KeyManagementBackedAccountService::class.java)

        val accountState = createAccount(mockNet, accountService, "Alice")

        val future = nodeIssuer.startFlow(CashIssueFlow(accountState.state.data, 100.USD))

        mockNet.runNetwork()

        val signedTx = future.getOrThrow()

        for (node in listOf(nodeIssuer, nodeReceiver)) {
            val recordedTx = node.services.validatedTransactions.getTransaction(signedTx.id)
            val txOutputs = recordedTx!!.tx.outputs

            assert(txOutputs.size == 1)

            val recordedState = txOutputs[0].data as FungibleToken

            val account = nodeReceiver.transaction {
                accountService.accountInfo(recordedState.holder.owningKey)!!
            }

            assertEquals(10000, recordedState.amount.quantity)
            assertEquals("USD", recordedState.issuedTokenType.tokenType.tokenIdentifier)
            assertEquals("Alice", account.state.data.name)
            assertEquals(nodeIssuer.info.legalIdentities.first(), recordedState.issuer)
        }
    }
}