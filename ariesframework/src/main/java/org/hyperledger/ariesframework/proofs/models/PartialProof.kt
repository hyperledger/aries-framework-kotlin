package org.hyperledger.ariesframework.proofs.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProofIdentifier(
    @SerialName("schema_id")
    val schemaId: String,
    @SerialName("cred_def_id")
    val credentialDefinitionId: String,
    @SerialName("rev_reg_id")
    val revocationRegistryId: String? = null,
    val timestamp: Int? = null,
)

@Serializable
data class PartialProof(
    val identifiers: List<ProofIdentifier>,
)
