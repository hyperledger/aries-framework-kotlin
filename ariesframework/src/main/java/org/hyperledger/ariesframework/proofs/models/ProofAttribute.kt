package org.hyperledger.ariesframework.proofs.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProofAttribute(
    @SerialName("sub_proof_index")
    val subProofIndex: String,
    val raw: String,
    val encoded: String,
)
