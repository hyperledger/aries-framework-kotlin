package org.hyperledger.ariesframework.proofs.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RequestedProof(
    @SerialName("revealed_attrs")
    val revealedAttributes: Map<String, ProofAttribute>,
    @SerialName("self_attested_attrs")
    val selfAttestedAttributes: Map<String, String>,
)
