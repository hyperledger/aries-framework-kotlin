package org.hyperledger.ariesframework.proofs.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Return type of Anoncreds.proverGetCredentialsForProofReq()
@Serializable
data class CredentialsForProof(
    val attrs: Map<String, List<CredInfoForProof>>,
    val predicates: Map<String, List<CredInfoForProof>>,
)

@Serializable
data class CredInfoForProof(
    @SerialName("cred_info")
    val credentialInfo: IndyCredentialInfo,
)

@Serializable
data class IndyCredentialInfo(
    val referent: String,
    @SerialName("attrs")
    val attributes: Map<String, String>,
    @SerialName("schema_id")
    val schemaId: String,
    @SerialName("cred_def_id")
    val credentialDefinitionId: String,
    @SerialName("rev_reg_id")
    val revocationRegistryId: String? = null,
    @SerialName("cred_rev_id")
    val credentialRevocationId: String? = null,
)
