package org.hyperledger.ariesframework.storage

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.Tags

@Serializable
class TestRecord(
    @EncodeDefault
    override var id: String = generateId(),
    override var _tags: Tags? = null,
    @EncodeDefault
    override val createdAt: Instant = Clock.System.now(),
    override var updatedAt: Instant? = null,
    var foo: String,
) : BaseRecord() {
    override fun getTags(): Tags {
        return _tags ?: emptyMap()
    }

    companion object {
        val type = "TestRecord"
    }
}
