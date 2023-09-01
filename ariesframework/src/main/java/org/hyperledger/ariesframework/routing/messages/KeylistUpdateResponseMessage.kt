package org.hyperledger.ariesframework.routing.messages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.AgentMessage

@Serializable
enum class KeylistUpdateResult {
    @SerialName("client_error")
    CLIENT_ERROR,

    @SerialName("server_error")
    SERVER_ERROR,

    @SerialName("no_change")
    NO_CHANGE,

    @SerialName("success")
    SUCCESS,
}

@Serializable
data class KeylistUpdated(
    @SerialName("recipient_key")
    val recipientKey: String,
    val action: KeylistUpdateAction,
    val result: KeylistUpdateResult,
)

@Serializable
class KeylistUpdateResponseMessage(
    val updated: List<KeylistUpdated>,
) : AgentMessage(generateId(), KeylistUpdateResponseMessage.type) {
    companion object {
        const val type = "https://didcomm.org/coordinate-mediation/1.0/keylist-update-response"
    }
}
