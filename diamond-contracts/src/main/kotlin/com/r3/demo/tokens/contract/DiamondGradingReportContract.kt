package com.r3.demo.tokens.contract

import com.r3.corda.lib.tokens.contracts.EvolvableTokenContract
import com.r3.demo.tokens.state.DiamondGradingReport
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction
import java.math.BigDecimal

/**
 * The [DiamondGradingReportContract] is inspired by the grading reports issued by the Gemological Institute of America
 * (GIA). For more excellent information on diamond grading, please see the (GIA's website)[http://www.gia.edu].
 */
class DiamondGradingReportContract : EvolvableTokenContract(), Contract {

    override fun additionalCreateChecks(tx: LedgerTransaction) {
        val outDiamond = tx.outputsOfType<DiamondGradingReport>().first()
        requireThat {
            "Diamond's carat weight must be greater than 0 (zero)" using (outDiamond.caratWeight > BigDecimal.ZERO)
        }
    }

    override fun additionalUpdateChecks(tx: LedgerTransaction) {
        val outDiamond = tx.outputsOfType<DiamondGradingReport>().first()
        requireThat {
            "Diamond's carat weight must be greater than 0 (zero)" using (outDiamond.caratWeight > BigDecimal.ZERO)
        }
    }
}