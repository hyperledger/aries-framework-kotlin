package org.hyperledger.ariesframework.proofs.messages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.AgentMessage
import org.hyperledger.ariesframework.agent.decorators.Attachment

@Serializable
class RequestPresentationMessage(
    val comment: String? = null,
    @SerialName("request_presentations~attach")
    val requestPresentationAttachments: List<Attachment>,
) : AgentMessage(generateId(), RequestPresentationMessage.type) {
    companion object {
        const val INDY_PROOF_REQUEST_ATTACHMENT_ID = "libindy-request-presentation-0"
        const val type = "https://didcomm.org/present-proof/1.0/request-presentation"
    }

    fun getRequestPresentationAttachmentById(id: String): Attachment? {
        return requestPresentationAttachments.firstOrNull { it.id == id }
    }

    fun indyProofRequest(): String {
        val attachment = getRequestPresentationAttachmentById(INDY_PROOF_REQUEST_ATTACHMENT_ID)
        return attachment?.getDataAsString() ?: throw Exception("Request presentation attachment not found")
    }
}
