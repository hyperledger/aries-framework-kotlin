package org.hyperledger.ariesframework.connection.messages

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.AgentMessage
import org.hyperledger.ariesframework.agent.decorators.ThreadDecorator

@Serializable
class DidExchangeCompleteMessage() : AgentMessage(generateId(), type) {
    constructor(threadId: String, parentThreadId: String) : this() {
        thread = ThreadDecorator(threadId, parentThreadId)
    }

    companion object {
        const val type = "https://didcomm.org/didexchange/1.1/complete"
    }
}
