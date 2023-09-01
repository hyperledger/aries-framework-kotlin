package org.hyperledger.ariesframework.routing.messages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.AgentMessage

@Serializable
class BatchPickupMessage(
    @SerialName("batch_size")
    val batchSize: Int,
) : AgentMessage(generateId(), BatchPickupMessage.type) {
    companion object {
        const val type = "https://didcomm.org/messagepickup/1.0/batch-pickup"
    }
}
