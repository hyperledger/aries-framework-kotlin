package org.hyperledger.ariesframework.storage

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.Tags
import org.hyperledger.ariesframework.agent.AgentMessage
import org.hyperledger.ariesframework.agent.MessageSerializer

@Serializable
enum class DidCommMessageRole {
    @SerialName("sender")
    Sender,

    @SerialName("receiver")
    Receiver,
}

@Serializable
data class DidCommMessageRecord private constructor(
    @EncodeDefault
    override var id: String = generateId(),
    override var _tags: Tags? = null,
    @EncodeDefault
    override val createdAt: Instant = Clock.System.now(),
    override var updatedAt: Instant? = null,
    var message: String,
    var role: DidCommMessageRole,
    var associatedRecordId: String?,
) : BaseRecord() {
    constructor(
        message: AgentMessage,
        role: DidCommMessageRole,
        associatedRecordId: String? = null,
    ) : this(
        message = message.toJsonString(),
        role = role,
        associatedRecordId = associatedRecordId,
    )

    override fun getTags(): Tags {
        val tags = (_tags ?: mutableMapOf()).toMutableMap()

        tags["role"] = role.name
        associatedRecordId?.let {
            tags["associatedRecordId"] = it
        }
        val agentMessage = MessageSerializer.decodeFromString(message)
        tags["messageId"] = agentMessage.id
        tags["messageType"] = agentMessage.type

        return tags
    }
}
