package org.hyperledger.ariesframework.proofs.models

import kotlinx.serialization.Serializable

@Serializable
data class RevocationRegistryDelta(
    val ver: String,
    val value: RevocationRegistryDeltaValue,
)

@Serializable
data class RevocationRegistryDeltaValue(
    val prevAccum: String? = null,
    val accum: String,
    val issued: List<Int>? = null,
    val revoked: List<Int>? = null,
)
