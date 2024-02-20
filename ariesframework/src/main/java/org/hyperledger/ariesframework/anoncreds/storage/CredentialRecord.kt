package org.hyperledger.ariesframework.anoncreds.storage

import anoncreds_uniffi.Credential
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.Tags
import org.hyperledger.ariesframework.storage.BaseRecord

@Serializable
class CredentialRecord(
    @EncodeDefault
    override var id: String,
    override var _tags: Tags? = null,
    @EncodeDefault
    override val createdAt: Instant,
    override var updatedAt: Instant? = null,

    var credentialId: String,
    var credentialRevocationId: String? = null,
    var revocationRegistryId: String? = null,
    var linkSecretId: String,
    var credential: String,
    var schemaId: String,
    var schemaName: String,
    var schemaVersion: String,
    var schemaIssuerId: String,
    var issuerId: String,
    var credentialDefinitionId: String,
) : BaseRecord() {
    constructor(
        tags: Tags? = null,
        credentialId: String,
        credentialRevocationId: String? = null,
        revocationRegistryId: String? = null,
        linkSecretId: String,
        credentialObject: Credential,
        schemaId: String,
        schemaName: String,
        schemaVersion: String,
        schemaIssuerId: String,
        issuerId: String,
        credentialDefinitionId: String,
    ) : this(
        BaseRecord.generateId(),
        tags,
        Clock.System.now(),
        null,
        credentialId,
        credentialRevocationId,
        revocationRegistryId,
        linkSecretId,
        credentialObject.toJson(),
        schemaId,
        schemaName,
        schemaVersion,
        schemaIssuerId,
        issuerId,
        credentialDefinitionId,
    ) {
        val tagMap = (tags ?: mutableMapOf()).toMutableMap()
        for ((key, value) in credentialObject.values()) {
            tagMap["attr::$key::value"] = value
            tagMap["attr::$key::marker"] = "1"
        }
        _tags = tagMap
    }

    override fun getTags(): Tags {
        val tags = (_tags ?: mutableMapOf()).toMutableMap()
        tags["credentialId"] = credentialId
        credentialRevocationId?.let { tags["credentialRevocationId"] = it }
        revocationRegistryId?.let { tags["revocationRegistryId"] = it }
        tags["linkSecretId"] = linkSecretId
        tags["schemaId"] = schemaId
        tags["schemaName"] = schemaName
        tags["schemaVersion"] = schemaVersion
        tags["schemaIssuerId"] = schemaIssuerId
        tags["issuerId"] = issuerId
        tags["credentialDefinitionId"] = credentialDefinitionId
        return tags
    }
}
