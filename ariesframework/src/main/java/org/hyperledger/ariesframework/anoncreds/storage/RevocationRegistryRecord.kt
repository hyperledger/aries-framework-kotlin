package org.hyperledger.ariesframework.anoncreds.storage

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.Tags
import org.hyperledger.ariesframework.storage.BaseRecord

@Serializable
class RevocationRegistryRecord(
    @EncodeDefault
    override var id: String = BaseRecord.generateId(),
    override var _tags: Tags? = null,
    @EncodeDefault
    override val createdAt: Instant = Clock.System.now(),
    override var updatedAt: Instant? = null,

    var credDefId: String,
    var revocRegId: String,
    var revocRegDef: String,
    var revocRegPrivate: String,
    var revocStatusList: String,
    var registryIndex: Int = 0,
) : BaseRecord() {
    override fun getTags(): Tags {
        val tags = (_tags ?: mutableMapOf()).toMutableMap()
        tags["credDefId"] = credDefId
        tags["revocRegId"] = revocRegId
        return tags
    }
}
