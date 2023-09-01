package org.hyperledger.ariesframework.routing.messages

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.AgentMessage

@Serializable
class MediationRequestMessage(
    @SerialName("sent_time")
    val sentTime: Instant,
) : AgentMessage(generateId(), MediationRequestMessage.type) {
    companion object {
        const val type = "https://didcomm.org/coordinate-mediation/1.0/mediate-request"
    }
}
