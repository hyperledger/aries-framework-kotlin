package org.hyperledger.ariesframework.routing.messages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.AgentMessage

@Serializable
enum class KeylistUpdateAction {
    @SerialName("add")
    ADD,

    @SerialName("remove")
    REMOVE,
}

@Serializable
data class KeylistUpdate(
    @SerialName("recipient_key")
    val recipientKey: String,
    val action: KeylistUpdateAction,
)

@Serializable
class KeylistUpdateMessage(
    val updates: List<KeylistUpdate>,
) : AgentMessage(generateId(), KeylistUpdateMessage.type) {
    companion object {
        const val type = "https://didcomm.org/coordinate-mediation/1.0/keylist-update"
    }
}
