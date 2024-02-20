package org.hyperledger.ariesframework.anoncreds

import askar_uniffi.AskarEntryOperation
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.proofs.models.AttributeFilter
import org.hyperledger.ariesframework.proofs.models.IndyCredentialInfo
import org.hyperledger.ariesframework.proofs.models.ProofRequest
import org.hyperledger.ariesframework.toJsonString
import org.slf4j.LoggerFactory

class AnoncredsService(val agent: Agent) {
    private val logger = LoggerFactory.getLogger(AnoncredsService::class.java)
    private val secretCategory = "link-secret-category"

    suspend fun createLinkSecret(): String {
        val linkSecretId = java.util.UUID.randomUUID().toString()
        val linkSecret = anoncreds_uniffi.createLinkSecret()
        agent.wallet.session!!.update(AskarEntryOperation.INSERT, secretCategory, linkSecretId, linkSecret.toByteArray(), null, null)
        return linkSecretId
    }

    suspend fun getLinkSecret(id: String): String {
        val linkSecret = agent.wallet.session!!.fetch(secretCategory, id, false)
            ?: throw Exception("Link secret not found for id $id")
        return String(linkSecret.value())
    }

    suspend fun getCredentialsForProofRequest(proofRequest: ProofRequest, referent: String): List<IndyCredentialInfo> {
        val requestedAttribute = proofRequest.requestedAttributes[referent] ?: proofRequest.requestedPredicates[referent]?.asProofAttributeInfo()
            ?: throw Exception("Referent not found in proof request")
        val tags = mutableMapOf<String, String>()
        if (requestedAttribute.names == null && requestedAttribute.name == null) {
            throw Exception("Proof request attribute must have either name or names")
        }
        if (requestedAttribute.names != null && requestedAttribute.name != null) {
            throw Exception("Proof request attribute cannot have both name and names")
        }
        val attributes = requestedAttribute.names ?: listOf(requestedAttribute.name!!)
        for (attribute in attributes) {
            tags["attr::$attribute::marker"] = "1"
        }
        if (requestedAttribute.restrictions != null) {
            val restrictionTag = queryFromRestrictions(requestedAttribute.restrictions)
            tags.putAll(restrictionTag)
        }
        val credentials = agent.credentialRepository.findByQuery(tags.toJsonString())
        return credentials.map { credentialRecord ->
            IndyCredentialInfo(
                credentialRecord.credentialId,
                emptyMap(), // We don't use attrs.
                credentialRecord.schemaId,
                credentialRecord.credentialDefinitionId,
                credentialRecord.revocationRegistryId,
                credentialRecord.credentialRevocationId,
            )
        }
    }

    private fun queryFromRestrictions(restrictions: List<AttributeFilter>): Map<String, String> {
        val tags = mutableMapOf<String, String>()
        for (restriction in restrictions) {
            restriction.schemaId?.let { tags["schemaId"] = it }
            restriction.schemaName?.let { tags["schemaName"] = it }
            restriction.schemaVersion?.let { tags["schemaVersion"] = it }
            restriction.schemaIssuerDid?.let { tags["schemaIssuerDid"] = it }
            restriction.issuerDid?.let { tags["issuerDid"] = it }
            restriction.credentialDefinitionId?.let { tags["credentialDefinitionId"] = it }
        }
        return tags
    }
}
