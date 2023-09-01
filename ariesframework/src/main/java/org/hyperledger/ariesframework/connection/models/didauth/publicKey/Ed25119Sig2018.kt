package org.hyperledger.ariesframework.connection.models.didauth.publicKey

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName(Ed25119Sig2018.type)
class Ed25119Sig2018(
    override val id: String,
    override val controller: String,
    val publicKeyBase58: String,
) : PublicKey() {
    override val value: String? = publicKeyBase58

    companion object {
        const val type = "Ed25519VerificationKey2018"
    }
}
