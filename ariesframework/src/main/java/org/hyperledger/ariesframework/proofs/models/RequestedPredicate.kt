package org.hyperledger.ariesframework.proofs.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RequestedPredicate(
    @SerialName("cred_id")
    val credentialId: String,
    val timestamp: Int? = null,
    @Transient
    var credentialInfo: IndyCredentialInfo? = null,
    @Transient
    var revoked: Boolean? = null,
)
