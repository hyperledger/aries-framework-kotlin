package org.hyperledger.ariesframework.connection.messages

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.AgentMessage
import org.hyperledger.ariesframework.connection.models.Connection

@Serializable
class ConnectionRequestMessage(
    val label: String,
    val imageUrl: String? = null,
    val connection: Connection,
) : AgentMessage(generateId(), ConnectionRequestMessage.type) {
    constructor(id: String, label: String, imageUrl: String? = null, connection: Connection) : this(label, imageUrl, connection) {
        this.id = id
    }

    companion object {
        const val type = "https://didcomm.org/connections/1.0/request"
    }
}
