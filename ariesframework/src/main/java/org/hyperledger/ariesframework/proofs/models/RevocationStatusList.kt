package org.hyperledger.ariesframework.proofs.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class RevocationStatusList(
    val issuerId: String,
    val currentAccumulator: String,
    val revRegDefId: String,
    val revocationList: List<Int>,
    val timestamp: Int,
) {
    fun toJsonString(): String {
        return Json.encodeToString(serializer(), this)
    }
}
