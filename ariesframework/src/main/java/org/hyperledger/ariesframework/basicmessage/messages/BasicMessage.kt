package org.hyperledger.ariesframework.basicmessage.messages

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.AgentMessage

@Serializable
class BasicMessage(
    val content: String,
) : AgentMessage(generateId(), BasicMessage.type) {
    companion object {
        const val type = "https://didcomm.org/basicmessage/1.0/message"
    }
}
