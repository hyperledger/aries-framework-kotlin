package org.hyperledger.ariesframework.agent.decorators

import askar_uniffi.AskarKeyAlg
import askar_uniffi.LocalKeyFactory
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.hyperledger.ariesframework.connection.models.Connection
import org.hyperledger.ariesframework.connection.models.didauth.didDocServiceModule
import org.hyperledger.ariesframework.decodeBase64url
import org.hyperledger.ariesframework.encodeBase64url
import org.hyperledger.ariesframework.util.Base58
import org.hyperledger.ariesframework.wallet.Wallet

@Serializable
class SignatureDecorator(
    @SerialName("@type")
    val signatureType: String,
    @SerialName("sig_data")
    val signatureData: String,
    val signer: String,
    val signature: String,
) {
    suspend fun unpackData(): ByteArray {
        val signedData = signatureData.decodeBase64url()
        if (signedData.size <= 8) {
            throw Exception("Invalid signature data")
        }

        val signature = signature.decodeBase64url()
        if (signature.isEmpty()) {
            throw Exception("Invalid signature")
        }

        val singerBytes = try {
            Base58.decode(signer)
        } catch (e: Exception) {
            throw Exception("Invalid signer: $signer")
        }
        val signKey = LocalKeyFactory().fromPublicBytes(AskarKeyAlg.ED25519, singerBytes)
        val isValid = signKey.verifySignature(signedData, signature, null)
        if (!isValid) {
            throw Exception("Signature verification failed")
        }

        // first 8 bytes are for 64 bit integer from unix epoch
        return signedData.copyOfRange(8, signedData.size)
    }

    suspend fun unpackConnection(): Connection {
        val signedData = unpackData()
        return Json { serializersModule = didDocServiceModule }.decodeFromString(String(signedData))
    }

    companion object {
        suspend fun signData(data: ByteArray, wallet: Wallet, verkey: String): SignatureDecorator {
            val signatureData = ByteArray(8) + data
            val signKey = wallet.session!!.fetchKey(verkey, false)
                ?: throw Exception("Key not found: $verkey")
            val signature = signKey.loadLocalKey().signMessage(signatureData, null)
            val signatureType = "https://didcomm.org/signature/1.0/ed25519Sha512_single"
            val signer = verkey
            return SignatureDecorator(
                signatureType,
                signatureData.encodeBase64url(),
                signer,
                signature.encodeBase64url(),
            )
        }
    }
}
