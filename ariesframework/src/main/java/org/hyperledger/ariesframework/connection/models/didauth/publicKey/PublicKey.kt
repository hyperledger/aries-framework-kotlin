package org.hyperledger.ariesframework.connection.models.didauth.publicKey

import kotlinx.serialization.Serializable

@Serializable
sealed class PublicKey {
    abstract val id: String
    abstract val controller: String
    abstract val value: String?
}
