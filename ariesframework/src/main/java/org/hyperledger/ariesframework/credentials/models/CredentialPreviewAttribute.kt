package org.hyperledger.ariesframework.credentials.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CredentialPreviewAttribute(
    val name: String,
    @SerialName("mime-type")
    val mimeType: String? = null,
    val value: String,
)
