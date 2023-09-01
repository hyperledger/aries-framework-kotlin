package org.hyperledger.ariesframework.credentials.messages

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.AckStatus
import org.hyperledger.ariesframework.agent.AgentMessage
import org.hyperledger.ariesframework.agent.decorators.ThreadDecorator

@Serializable
class CredentialAckMessage private constructor(
    val status: AckStatus,
) : AgentMessage(generateId(), CredentialAckMessage.type) {
    constructor(threadId: String, status: AckStatus) : this(status) {
        thread = ThreadDecorator(threadId)
    }
    companion object {
        const val type = "https://didcomm.org/issue-credential/1.0/ack"
    }

    override fun requestResponse(): Boolean {
        return false
    }
}
