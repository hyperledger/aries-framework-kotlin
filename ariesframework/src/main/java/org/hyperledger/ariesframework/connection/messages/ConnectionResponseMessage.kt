package org.hyperledger.ariesframework.connection.messages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.AgentMessage
import org.hyperledger.ariesframework.agent.decorators.SignatureDecorator

@Serializable
class ConnectionResponseMessage(
    @SerialName("connection~sig")
    val connectionSig: SignatureDecorator,
) : AgentMessage(generateId(), ConnectionResponseMessage.type) {
    companion object {
        const val type = "https://didcomm.org/connections/1.0/response"
    }
}
