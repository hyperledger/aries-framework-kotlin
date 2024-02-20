package org.hyperledger.ariesframework.anoncreds.storage

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.Tags
import org.hyperledger.ariesframework.storage.BaseRecord

@Serializable
class CredentialDefinitionRecord(
    @EncodeDefault
    override var id: String = BaseRecord.generateId(),
    override var _tags: Tags? = null,
    @EncodeDefault
    override val createdAt: Instant = Clock.System.now(),
    override var updatedAt: Instant? = null,

    var schemaId: String,
    var credDefId: String,
    var credDef: String,
    var credDefPriv: String,
    var keyCorrectnessProof: String,
) : BaseRecord() {
    override fun getTags(): Tags {
        val tags = (_tags ?: mutableMapOf()).toMutableMap()
        tags["schemaId"] = schemaId
        tags["credDefId"] = credDefId
        return tags
    }
}
