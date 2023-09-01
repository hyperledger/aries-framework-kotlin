package org.hyperledger.ariesframework.proofs.repository

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.Tags
import org.hyperledger.ariesframework.proofs.models.AutoAcceptProof
import org.hyperledger.ariesframework.proofs.models.ProofState
import org.hyperledger.ariesframework.storage.BaseRecord

@Serializable
data class ProofExchangeRecord(
    @EncodeDefault
    override var id: String = generateId(),
    override var _tags: Tags? = null,
    @EncodeDefault
    override val createdAt: Instant = Clock.System.now(),
    override var updatedAt: Instant? = null,

    var connectionId: String,
    var threadId: String,
    var isVerified: Boolean? = null,
    var presentationId: String? = null,
    var state: ProofState,
    var autoAcceptProof: AutoAcceptProof? = null,
    var errorMessage: String? = null,
) : BaseRecord() {
    override fun getTags(): Tags {
        val tags = (_tags ?: mutableMapOf()).toMutableMap()

        tags["threadId"] = threadId
        tags["connectionId"] = connectionId
        tags["state"] = state.name

        return tags
    }

    fun assertState(vararg expectedStates: ProofState) {
        if (!expectedStates.contains(this.state)) {
            throw Exception("Proof record is in invalid state ${this.state}. Valid states are: $expectedStates")
        }
    }

    fun assertConnection(currentConnectionId: String) {
        if (this.connectionId != currentConnectionId) {
            throw Exception(
                "Proof record is associated with connection '${this.connectionId}'." +
                    " Current connection is '$currentConnectionId'",
            )
        }
    }
}
