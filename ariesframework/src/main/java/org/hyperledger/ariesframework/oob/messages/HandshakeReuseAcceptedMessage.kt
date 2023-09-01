package org.hyperledger.ariesframework.oob.messages

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.AgentMessage
import org.hyperledger.ariesframework.agent.decorators.ThreadDecorator

@Serializable
class HandshakeReuseAcceptedMessage() : AgentMessage(generateId(), HandshakeReuseAcceptedMessage.type) {
    constructor(threadId: String, parentThreadId: String) : this() {
        this.thread = ThreadDecorator(threadId, parentThreadId)
    }

    companion object {
        const val type = "https://didcomm.org/out-of-band/1.1/handshake-reuse-accepted"
    }
}
