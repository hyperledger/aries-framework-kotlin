package org.hyperledger.ariesframework.proofs.models

import kotlinx.serialization.Serializable

@Serializable
class RevocationInterval(
    val from: Int? = null,
    val to: Int? = null,
)
