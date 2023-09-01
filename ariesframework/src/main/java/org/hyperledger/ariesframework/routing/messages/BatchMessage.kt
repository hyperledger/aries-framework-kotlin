package org.hyperledger.ariesframework.routing.messages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.EncryptedMessage
import org.hyperledger.ariesframework.agent.AgentMessage

@Serializable
class BatchMessageMessage(
    val id: String,
    val message: EncryptedMessage,
)

@Serializable
class BatchMessage(
    @SerialName("messages~attach")
    val messages: List<BatchMessageMessage>,
) : AgentMessage(generateId(), BatchMessage.type) {
    companion object {
        const val type = "https://didcomm.org/messagepickup/1.0/batch"
    }
}
