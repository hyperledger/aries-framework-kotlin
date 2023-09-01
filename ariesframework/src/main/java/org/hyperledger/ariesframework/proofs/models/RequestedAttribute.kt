package org.hyperledger.ariesframework.proofs.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RequestedAttribute(
    @SerialName("cred_id")
    val credentialId: String,
    val timestamp: Int? = null,
    val revealed: Boolean,
    @Transient
    var credentialInfo: IndyCredentialInfo? = null,
    @Transient
    var revoked: Boolean? = null,
)
