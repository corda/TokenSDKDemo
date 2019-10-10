package com.r3.demo.tokens.flows

import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.services.KeyManagementBackedAccountService
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.demo.tokens.state.DiamondGradingReport
import net.corda.core.contracts.Amount
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

fun createAccount(mockNet: MockNetwork, accountService: KeyManagementBackedAccountService, name: String): StateAndRef<AccountInfo> {
    val accountFuture = accountService.createAccount(name)

    mockNet.runNetwork()

    return accountFuture.getOrThrow()
}

fun createReport(mockNet: MockNetwork, nodeIssuer: StartedMockNode, nodeDealer: StartedMockNode): DiamondGradingReport {
    val issuer = nodeIssuer.info.legalIdentities.first()
    val dealer = nodeDealer.info.legalIdentities.first()
    val report = DiamondGradingReport("1.0", DiamondGradingReport.ColorScale.D, DiamondGradingReport.ClarityScale.VI1, "oval", issuer, dealer)

    val reportFuture = nodeIssuer.startFlow(CreateDiamondGradingReportFlow(report))

    mockNet.runNetwork()

    reportFuture.getOrThrow()

    return report
}

fun issueCash(mockNet: MockNetwork, nodeIssuer: StartedMockNode, account: StateAndRef<AccountInfo>, amount: Amount<TokenType>) {
    val issueFuture = nodeIssuer.startFlow(CashIssueFlow(account.state.data, amount))

    mockNet.runNetwork()

    issueFuture.getOrThrow()
}

fun retrieveDiamondTokenId(node: StartedMockNode, tx: SignedTransaction): UniqueIdentifier {
    val recordedTx = node.services.validatedTransactions.getTransaction(tx.id)
    val txOutputs = recordedTx!!.tx.outputs

    return txOutputs.map { it.data }.filterIsInstance(NonFungibleToken::class.java).map{ it.linearId }.first()
}

fun verifyAccountWallet(node: StartedMockNode, account: StateAndRef<AccountInfo>, count: Int, amount: Long) {
    val criteria = QueryCriteria.VaultQueryCriteria(
            externalIds = listOf(account.state.data.identifier.id),
            status = Vault.StateStatus.UNCONSUMED
    )

    val vault = node.services.vaultService.queryBy<ContractState>(FungibleToken::class.java, criteria = criteria).states

    if (count > 0 || amount == 0L){
        assertEquals(count, vault.size)
    }

    var sum: Long = 0

    vault.forEach {
        val token = it.state.data as FungibleToken
        assertEquals("USD", token.issuedTokenType.tokenType.tokenIdentifier)
        sum += token.amount.quantity
    }

    assertEquals(amount * 100, sum)
}

fun verifyGradingReport(node: StartedMockNode, report: DiamondGradingReport) {
    val criteria = QueryCriteria.VaultQueryCriteria(
            status = Vault.StateStatus.UNCONSUMED
    )
    val vault = node.services.vaultService.queryBy<ContractState>(DiamondGradingReport::class.java, criteria = criteria).states

    assertEquals(1, vault.size)

    val token = vault.first().state.data as DiamondGradingReport

    assertEquals(report.caratWeight, token.caratWeight)
}

fun verifyDiamondTokenPresent(node: StartedMockNode, account: StateAndRef<AccountInfo>) {
    val criteria = QueryCriteria.VaultQueryCriteria(
            externalIds = listOf(account.state.data.identifier.id),
            status = Vault.StateStatus.UNCONSUMED
    )
    val vault = node.services.vaultService.queryBy<ContractState>(NonFungibleToken::class.java, criteria = criteria).states

    assertEquals(1, vault.size)
    assertTrue(vault.first().state.data.toString().contains("TokenPointer(class com.r3.demo.tokens.state.DiamondGradingReport"))
}

fun verifyDiamondTokenMissing(node: StartedMockNode, account: StateAndRef<AccountInfo>) {
    val criteria = QueryCriteria.VaultQueryCriteria(
            externalIds = listOf(account.state.data.identifier.id),
            status = Vault.StateStatus.UNCONSUMED
    )
    val vault = node.services.vaultService.queryBy<ContractState>(NonFungibleToken::class.java, criteria = criteria).states

    assertEquals(0, vault.size)
}
