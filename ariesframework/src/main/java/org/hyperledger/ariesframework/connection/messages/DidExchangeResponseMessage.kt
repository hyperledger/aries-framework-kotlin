package org.hyperledger.ariesframework.connection.messages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.AgentMessage
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.agent.decorators.ThreadDecorator

@Serializable
class DidExchangeResponseMessage(
    val did: String,
    @SerialName("did_doc~attach")
    var didDoc: Attachment? = null,
    @SerialName("did_rotate~attach")
    var didRotate: Attachment? = null,
) : AgentMessage(generateId(), type) {
    constructor(threadId: String, did: String, didDoc: Attachment? = null, didRotate: Attachment? = null) : this(did, didDoc, didRotate) {
        this.thread = ThreadDecorator(threadId)
    }
    companion object {
        const val type = "https://didcomm.org/didexchange/1.1/response"
    }
}
