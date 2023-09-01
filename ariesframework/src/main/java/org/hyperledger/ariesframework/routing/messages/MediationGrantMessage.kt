package org.hyperledger.ariesframework.routing.messages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.AgentMessage

@Serializable
class MediationGrantMessage(
    @SerialName("routing_keys")
    val routingKeys: List<String>,
    val endpoint: String,
) : AgentMessage(generateId(), MediationGrantMessage.type) {
    companion object {
        const val type = "https://didcomm.org/coordinate-mediation/1.0/mediate-grant"
    }
}
