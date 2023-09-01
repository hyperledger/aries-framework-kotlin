package org.hyperledger.ariesframework.credentials.models

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class CredentialPreview(
    val attributes: List<CredentialPreviewAttribute>,
    @SerialName("@type")
    @EncodeDefault
    val type: String = "https://didcomm.org/issue-credential/1.0/credential-preview",
) {
    companion object {
        fun fromDictionary(dic: Map<String, String>): CredentialPreview {
            val attributes = dic.map { (key, value) ->
                CredentialPreviewAttribute(key, "text/plain", value)
            }
            return CredentialPreview(attributes)
        }
    }
}
