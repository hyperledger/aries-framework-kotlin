package org.hyperledger.ariesframework.routing.repository

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.Tags
import org.hyperledger.ariesframework.storage.BaseRecord
import org.hyperledger.ariesframework.toJsonString

@Serializable
enum class MediationState {
    @SerialName("Requested")
    Requested,

    @SerialName("Granted")
    Granted,

    @SerialName("Denied")
    Denied,
}

@Serializable
enum class MediationRole {
    @SerialName("MEDIATOR")
    Mediator,

    @SerialName("RECIPIENT")
    Recipient,
}

@Serializable
data class MediationRecord(
    @EncodeDefault
    override var id: String = generateId(),
    override var _tags: Tags? = null,
    @EncodeDefault
    override val createdAt: Instant = Clock.System.now(),
    override var updatedAt: Instant? = null,
    var state: MediationState,
    val role: MediationRole,
    val connectionId: String,
    val threadId: String,
    var endpoint: String? = null,
    val recipientKeys: List<String> = emptyList(),
    var routingKeys: List<String> = emptyList(),
    val invitationUrl: String,
) : BaseRecord() {
    override fun getTags(): Tags {
        var tags = (_tags ?: mutableMapOf()).toMutableMap()

        tags["state"] = state.name
        tags["role"] = role.name
        tags["connectionId"] = connectionId
        tags["threadId"] = threadId
        tags["recipientKeys"] = recipientKeys.toJsonString()

        return tags
    }

    fun isReady(): Boolean {
        return state in listOf(MediationState.Granted)
    }

    fun assertReady() {
        if (!isReady()) {
            throw Exception(
                "Mediation record is not ready to be used. Expected ${MediationState.Granted}, found invalid state $state",
            )
        }
    }

    fun assertState(vararg expectedStates: MediationState) {
        if (state !in expectedStates) {
            throw Exception("Mediation record is in invalid state $state. Valid states are: ${expectedStates.joinToString { it.name }}.")
        }
    }

    fun assertRole(expectedRole: MediationRole) {
        if (role != expectedRole) {
            throw Exception("Mediation record has invalid role $role. Expected role $expectedRole.")
        }
    }
}
