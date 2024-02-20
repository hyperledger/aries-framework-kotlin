package org.hyperledger.ariesframework.proofs.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class VersionedRevocationRegistryDelta(
    val ver: String,
    val value: RevocationRegistryDelta,
)

@Serializable
data class RevocationRegistryDelta(
    val prevAccum: String? = null,
    val accum: String,
    val issued: List<Int>? = null,
    val revoked: List<Int>? = null,
) {
    fun toJsonString(): String {
        return Json.encodeToString(this)
    }

    fun toVersionedJson(): String {
        return Json.encodeToString(VersionedRevocationRegistryDelta("1.0", this))
    }
}
