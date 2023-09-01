package org.hyperledger.ariesframework.connection.messages

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.AgentMessage

@Serializable
class TrustPingMessage(
    val comment: String? = null,
    @SerialName("response_requested")
    @EncodeDefault
    val responseRequested: Boolean = false,
) : AgentMessage(generateId(), TrustPingMessage.type) {
    companion object {
        const val type = "https://didcomm.org/trust_ping/1.0/ping"
    }

    override fun requestResponse(): Boolean {
        return responseRequested
    }
}
