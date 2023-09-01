package org.hyperledger.ariesframework.connection.models.didauth.publicKey

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName(RsaSig2018.type)
class RsaSig2018(
    override val id: String,
    override val controller: String,
    val publicKeyPem: String,
) : PublicKey() {
    override val value: String? = publicKeyPem

    companion object {
        const val type = "RsaVerificationKey2018"
    }
}
