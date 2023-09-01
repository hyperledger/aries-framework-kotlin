package org.hyperledger.ariesframework.credentials.messages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.AgentMessage
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.credentials.models.CredentialPreview

@Serializable
class OfferCredentialMessage(
    val comment: String? = null,
    @SerialName("credential_preview")
    val credentialPreview: CredentialPreview,
    @SerialName("offers~attach")
    val offerAttachments: List<Attachment>,
) : AgentMessage(generateId(), OfferCredentialMessage.type) {
    companion object {
        const val INDY_CREDENTIAL_OFFER_ATTACHMENT_ID = "libindy-cred-offer-0"
        const val type = "https://didcomm.org/issue-credential/1.0/offer-credential"
    }

    fun getOfferAttachmentById(id: String): Attachment? {
        return offerAttachments.firstOrNull { it.id == id }
    }

    fun getCredentialOffer(): String {
        val attachment = getOfferAttachmentById(OfferCredentialMessage.INDY_CREDENTIAL_OFFER_ATTACHMENT_ID)
        return attachment?.getDataAsString() ?: throw Exception("Credential offer attachment not found")
    }
}
