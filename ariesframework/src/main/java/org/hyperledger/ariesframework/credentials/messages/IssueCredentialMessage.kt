package org.hyperledger.ariesframework.credentials.messages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.AgentMessage
import org.hyperledger.ariesframework.agent.decorators.Attachment

@Serializable
class IssueCredentialMessage(
    val comment: String? = null,
    @SerialName("credentials~attach")
    val credentialAttachments: List<Attachment>,
) : AgentMessage(generateId(), IssueCredentialMessage.type) {
    companion object {
        const val INDY_CREDENTIAL_ATTACHMENT_ID = "libindy-cred-0"
        const val type = "https://didcomm.org/issue-credential/1.0/issue-credential"
    }

    fun getCredentialAttachmentById(id: String): Attachment? {
        return credentialAttachments.firstOrNull { it.id == id }
    }
}
