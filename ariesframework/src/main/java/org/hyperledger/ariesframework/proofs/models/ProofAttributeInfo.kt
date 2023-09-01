package org.hyperledger.ariesframework.proofs.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProofAttributeInfo(
    val name: String? = null,
    val names: List<String>? = null,
    @SerialName("non_revoked")
    val nonRevoked: RevocationInterval? = null,
    val restrictions: List<AttributeFilter>? = null,
)
