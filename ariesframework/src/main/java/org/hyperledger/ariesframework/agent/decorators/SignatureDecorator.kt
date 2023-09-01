package org.hyperledger.ariesframework.agent.decorators

import kotlinx.coroutines.future.await
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.hyperledger.ariesframework.connection.models.Connection
import org.hyperledger.ariesframework.connection.models.didauth.didDocServiceModule
import org.hyperledger.ariesframework.decodeBase64url
import org.hyperledger.ariesframework.encodeBase64url
import org.hyperledger.ariesframework.wallet.Wallet
import org.hyperledger.indy.sdk.crypto.Crypto

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

        val isValid = Crypto.cryptoVerify(signer, signedData, signature).await()
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
            val signature = Crypto.cryptoSign(wallet.indyWallet, verkey, signatureData).await()
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
