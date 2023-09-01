package org.hyperledger.ariesframework.connection.models.didauth.publicKey

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName(EddsaSaSigSecp256k1.type)
class EddsaSaSigSecp256k1(
    override val id: String,
    override val controller: String,
    val publicKeyHex: String,
) : PublicKey() {
    override val value: String? = publicKeyHex

    companion object {
        const val type = "Secp256k1VerificationKey2018"
    }
}
