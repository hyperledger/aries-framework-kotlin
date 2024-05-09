package org.hyperledger.ariesframework.connection.messages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.AgentMessage

@Serializable
class DidExchangeRequestMessage(
    val label: String,
    @SerialName("goal_code")
    val goalCode: String? = null,
    val goal: String? = null,
    val did: String,
) : AgentMessage(generateId(), type) {
    companion object {
        const val type = "https://didcomm.org/didexchange/1.1/request"
    }
}
