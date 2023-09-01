package org.hyperledger.ariesframework.routing.messages

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.AgentMessage

@Serializable
class MediationDenyMessage() : AgentMessage(generateId(), MediationDenyMessage.type) {
    companion object {
        const val type = "https://didcomm.org/coordinate-mediation/1.0/mediate-deny"
    }
}
