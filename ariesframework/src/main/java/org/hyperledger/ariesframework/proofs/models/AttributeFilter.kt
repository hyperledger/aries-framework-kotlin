package org.hyperledger.ariesframework.proofs.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class AttributeFilter(
    @SerialName("schema_id")
    val schemaId: String? = null,
    @SerialName("schema_name")
    val schemaName: String? = null,
    @SerialName("schema_version")
    val schemaVersion: String? = null,
    @SerialName("schema_issuer_did")
    val schemaIssuerDid: String? = null,
    @SerialName("issuer_did")
    val issuerDid: String? = null,
    @SerialName("cred_def_id")
    val credentialDefinitionId: String? = null,
)
