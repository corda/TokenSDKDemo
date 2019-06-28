package com.r3.vaultrecycler.explorer

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import java.util.ArrayList

/**
 * Data object used to record a node in the transaction graph.
 */
data class TransactionNode(
    val txId: SecureHash,
    var inputs: Array<StateRef>,
    var outputs: ArrayList<SecureHash>,
    var references: Array<StateRef>,
    var attachments: Array<SecureHash>,
    var outputStates: Array<Class<ContractState>>,
    var outputConsumed: Int = 0,
    var outputParticipant: Int = 0,
    var outputsCreated: Int = 0,
    var outputsUsed: Int = 0,
    var active: Boolean = false,
    var queued: Boolean = false
){
    /**
     * General purpose int field that can be used by explorers
     */
    @Suppress("UNUSED")
    var index = 0

    /**
     * General purpose field that can be used by explorers
     */
    @Suppress("UNUSED")
    var value: Any? = null

    @Suppress("UNUSED")
    fun isLeaf() = outputsCreated == 0

    @Suppress("UNUSED")
    fun isRoot() = inputs.isEmpty()

    /**
     * Returns true if all outputs are consumed
     */
    fun isFullyConsumed() = outputsCreated == outputsUsed

    /**
     * Returns true if the only unconsumed outputs do
     * not involve the user
     */
    @Suppress("UNUSED")
    fun isFullyConsumedParticipant(): Boolean {
        if (isFullyConsumed()){
            return true
        }

        return (outputConsumed xor outputParticipant) == 0
    }

    fun isFullyConsumed(aggressive : Boolean): Boolean{
        return if (aggressive) isFullyConsumedParticipant() else isFullyConsumed()
    }

    /**
     * Record that an output state has been used within the vault.
     */
    fun recordOutputState(txId: SecureHash, index: Int){
        outputs.add(txId)
        outputsUsed++
        outputConsumed = outputConsumed or (1 shl index)

        if (isFullyConsumed()){
            queued = false
        }
    }

    /**
     * Record whether the user is a participant of the output state
     */
    fun recordOutputParticipant(participant: Boolean, index: Int){
        if (participant){
            outputParticipant = outputParticipant or (1 shl index)
        }
    }

    /**
     * Indicates whether an output state has been used within the vault
     */
    fun isOutputStateConsumed(index: Int): Boolean{
        return (outputConsumed and (1 shl index)) > 0
    }

    /**
     * Indicates whether the user is a participant in the output state
     */
    fun isOutputParticipant(index: Int): Boolean{
        return (outputParticipant and (1 shl index)) > 0
    }

    /**
     * Constructor used for creating original objects.
     */
    constructor(txId: SecureHash) :
            this(txId,
                emptyArray<StateRef>(),
                ArrayList<SecureHash>(),
                emptyArray<StateRef>(),
                emptyArray<SecureHash>(),
                emptyArray<Class<ContractState>>())

    /**
     * Constructor used for creating placeholders.
     */
    constructor(txId: SecureHash, id: SecureHash, index: Int) :
            this(txId,
                emptyArray<StateRef>(),
                ArrayList<SecureHash>(),
                emptyArray<StateRef>(),
                emptyArray<SecureHash>(),
                emptyArray<Class<ContractState>>()){
        outputs.add(id)
        outputsUsed = 1
        outputsCreated = 1
        outputConsumed = 1 shl index
    }

    override fun equals(other: Any?): Boolean {
        return super.equals(other)
    }

    override fun hashCode(): Int {
        return super.hashCode()
    }
}
