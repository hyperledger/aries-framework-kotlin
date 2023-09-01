package org.hyperledger.ariesframework.credentials.models

import org.hyperledger.ariesframework.connection.repository.ConnectionRecord

class CreateProposalOptions(
    val connection: ConnectionRecord,
    val credentialPreview: CredentialPreview? = null,
    val schemaIssuerDid: String? = null,
    val schemaId: String? = null,
    val schemaName: String? = null,
    val schemaVersion: String? = null,
    val credentialDefinitionId: String? = null,
    val issuerDid: String? = null,
    val autoAcceptCredential: AutoAcceptCredential? = null,
    val comment: String? = null,
)

class CreateOfferOptions(
    val connection: ConnectionRecord? = null,
    val credentialDefinitionId: String,
    val attributes: List<CredentialPreviewAttribute>,
    val autoAcceptCredential: AutoAcceptCredential? = null,
    val comment: String? = null,
)

class AcceptOfferOptions(
    val credentialRecordId: String,
    val holderDid: String? = null,
    val autoAcceptCredential: AutoAcceptCredential? = null,
    val comment: String? = null,
)

class AcceptRequestOptions(
    val credentialRecordId: String,
    val autoAcceptCredential: AutoAcceptCredential? = null,
    val comment: String? = null,
)

class AcceptCredentialOptions(
    val credentialRecordId: String,
)
