package org.hyperledger.ariesframework.proofs.messages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.AgentMessage
import org.hyperledger.ariesframework.agent.decorators.Attachment

@Serializable
class PresentationMessage(
    val comment: String? = null,
    @SerialName("presentations~attach")
    val presentationAttachments: List<Attachment>,
) : AgentMessage(generateId(), PresentationMessage.type) {
    companion object {
        const val INDY_PROOF_ATTACHMENT_ID = "libindy-presentation-0"
        const val type = "https://didcomm.org/present-proof/1.0/presentation"
    }

    fun getPresentationAttachmentById(id: String): Attachment? {
        return presentationAttachments.firstOrNull { it.id == id }
    }

    fun indyProof(): String {
        val attachment = getPresentationAttachmentById(INDY_PROOF_ATTACHMENT_ID)
        return attachment?.getDataAsString() ?: throw Exception("Presentation attachment not found")
    }
}
