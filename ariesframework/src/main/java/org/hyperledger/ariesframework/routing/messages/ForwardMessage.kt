package org.hyperledger.ariesframework.routing.messages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.EncryptedMessage
import org.hyperledger.ariesframework.agent.AgentMessage

@Serializable
class ForwardMessage(
    val to: String,
    @SerialName("msg")
    val message: EncryptedMessage,
) : AgentMessage(generateId(), ForwardMessage.type) {
    companion object {
        const val type = "https://didcomm.org/routing/1.0/forward"
    }
}
