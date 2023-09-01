package org.hyperledger.ariesframework.proofs.models

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ProofRequest(
    @EncodeDefault
    val name: String = "proof-request",
    @EncodeDefault
    val version: String = "1.0",
    val nonce: String,
    @SerialName("requested_attributes")
    val requestedAttributes: Map<String, ProofAttributeInfo>,
    @SerialName("requested_predicates")
    val requestedPredicates: Map<String, ProofPredicateInfo>,
    @SerialName("non_revoked")
    val nonRevoked: RevocationInterval? = null,
    val ver: String? = null,
) {
    fun toJsonString(): String = Json.encodeToString(this)
}
