package org.hyperledger.ariesframework.oob.repository

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.Tags
import org.hyperledger.ariesframework.oob.messages.OutOfBandInvitation
import org.hyperledger.ariesframework.oob.models.OutOfBandRole
import org.hyperledger.ariesframework.oob.models.OutOfBandState
import org.hyperledger.ariesframework.storage.BaseRecord

@Serializable
data class OutOfBandRecord(
    @EncodeDefault
    override var id: String = generateId(),
    override var _tags: Tags? = null,
    @EncodeDefault
    override val createdAt: Instant = Clock.System.now(),
    override var updatedAt: Instant? = null,
    var outOfBandInvitation: OutOfBandInvitation,
    var role: OutOfBandRole,
    var state: OutOfBandState,
    var reusable: Boolean,
    var autoAcceptConnection: Boolean? = null,
    var mediatorId: String? = null,
    var reuseConnectionId: String? = null,
) : BaseRecord() {
    override fun getTags(): Tags {
        var tags = (_tags ?: mutableMapOf()).toMutableMap()

        tags["state"] = state.name
        tags["role"] = role.name
        tags["invitationId"] = outOfBandInvitation.id
        outOfBandInvitation.invitationKey()?.let {
            tags["invitationKey"] = it
        }
        if (this.outOfBandInvitation.fingerprints().isNotEmpty()) {
            tags["recipientKeyFingerprint"] = this.outOfBandInvitation.fingerprints()[0]
        }

        return tags
    }

    fun assertState(vararg expectedStates: OutOfBandState) {
        if (!expectedStates.contains(this.state)) {
            throw Exception(
                "OutOfBand record is in invalid state ${this.state}. Valid states are: ${
                    expectedStates.joinToString(", ") {
                        it.name
                    }
                }.",
            )
        }
    }

    fun assertRole(expectedRole: OutOfBandRole) {
        if (this.role != expectedRole) {
            throw Exception("OutOfBand record has invalid role ${this.role}. Expected role $expectedRole.")
        }
    }
}
