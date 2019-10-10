package com.r3.demo.tokens.state

import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import com.r3.demo.tokens.contract.DiamondGradingReportContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import java.math.BigDecimal

/**
 * Represents a diamond grade report that can evolve over time.
 */
@BelongsToContract(DiamondGradingReportContract::class)
data class DiamondGradingReport(
        val caratWeight: BigDecimal,
        val color: ColorScale,
        val clarity: ClarityScale,
        val cut: String,
        val assessor: Party,
        val requester: AbstractParty,
        override val linearId: UniqueIdentifier
) : EvolvableTokenType() {
    @Suppress("unused")
    constructor(
            caratWeight: String,
            color: ColorScale,
            clarity: ClarityScale,
            cut: String,
            assessor: Party,
            requester: Party) : this(BigDecimal(caratWeight), color, clarity, cut, assessor, requester, UniqueIdentifier())
    @Suppress("unused")
    constructor(
            caratWeight: String,
            color: ColorScale,
            clarity: ClarityScale,
            cut: String,
            assessor: Party,
            requester: Party,
            linearId: UniqueIdentifier) : this(BigDecimal(caratWeight), color, clarity, cut, assessor, requester, linearId)

    @CordaSerializable enum class ColorScale { D, E, F, G, H, I, J, K, L, M, N }
    @CordaSerializable enum class ClarityScale { VVS1, VVS2, VS1, VS2, VI1, VI2 }

    override val maintainers: List<Party>
        get() = listOf(assessor)
    override val participants: List<AbstractParty>
        get() = setOf(assessor, requester).toList()
    override val fractionDigits = 0
}
