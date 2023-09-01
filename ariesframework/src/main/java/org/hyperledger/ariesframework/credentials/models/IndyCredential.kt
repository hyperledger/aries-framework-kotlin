package org.hyperledger.ariesframework.credentials.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class IndyCredential(
    @SerialName("schema_id")
    val schemaId: String,
    @SerialName("cred_def_id")
    val credentialDefinitionId: String,
    @SerialName("rev_reg_id")
    val revocationRegistryId: String? = null,
    @SerialName("cred_rev_id")
    val credentialRevocationId: String? = null,
)
