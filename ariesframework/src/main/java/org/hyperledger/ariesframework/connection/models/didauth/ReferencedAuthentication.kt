package org.hyperledger.ariesframework.connection.models.didauth

import kotlinx.serialization.Serializable

@Serializable
class ReferencedAuthentication(
    val type: String,
    val publicKey: String,
) : Authentication()
