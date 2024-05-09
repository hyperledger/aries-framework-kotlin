package org.hyperledger.ariesframework.connection

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.decorators.Jws
import org.hyperledger.ariesframework.agent.decorators.JwsFlattenedFormat
import org.hyperledger.ariesframework.agent.decorators.JwsGeneralFormat
import org.hyperledger.ariesframework.decodeBase64url
import org.hyperledger.ariesframework.encodeBase64url
import org.hyperledger.ariesframework.util.Base58
import org.hyperledger.ariesframework.util.DIDParser
import org.slf4j.LoggerFactory

class JwsService(val agent: Agent) {
    private val logger = LoggerFactory.getLogger(JwsService::class.java)

    /**
     * Create a JWS using the given payload and verkey.
     *
     * @param payload The payload to sign.
     * @param verkey The verkey to sign the payload for. The verkey should be created using [Wallet.createDid].
     * @return The created JWS.
     */
    suspend fun createJws(payload: ByteArray, verkey: String): JwsGeneralFormat {
        val keyEntry = agent.wallet.session!!.fetchKey(verkey, false)
            ?: throw Exception("Unable to find key for verkey: $verkey")
        val key = keyEntry.loadLocalKey()
        val jwk = Json.decodeFromString<JsonObject>(key.toJwkPublic(null))
        val protectedHeader = JsonObject(
            mapOf(
                "alg" to JsonPrimitive("EdDSA"),
                "jwk" to jwk,
            ),
        )
        val protectedHeaderJson = Json.encodeToString(protectedHeader)
        val base64ProtectedHeader = protectedHeaderJson.encodeToByteArray().encodeBase64url()
        val base64Payload = payload.encodeBase64url()

        val message = "$base64ProtectedHeader.$base64Payload".toByteArray()
        val signature = key.signMessage(message, null)
        val base64Signature = signature.encodeBase64url()
        val header = mapOf(
            "kid" to DIDParser.convertVerkeyToDidKey(verkey),
        )

        return JwsGeneralFormat(header, base64Signature, base64ProtectedHeader)
    }

    /**
     * Verify the given JWS against the given payload.
     *
     * @param jws The JWS to verify.
     * @param payload The payload to verify the JWS against.
     * @return A pair containing the validity of the JWS and the signer's verkey.
     */
    fun verifyJws(jws: Jws, payload: ByteArray): Pair<Boolean, String> {
        logger.debug("Verifying JWS...")
        val firstSig = when (jws) {
            is JwsGeneralFormat -> jws
            is JwsFlattenedFormat -> jws.signatures.first()
            else -> throw Exception("Unsupported JWS type")
        }
        val protected = Json.decodeFromString<JsonObject>(firstSig.protected.decodeBase64url().decodeToString())
        val signature = firstSig.signature.decodeBase64url()
        val jwk = protected["jwk"] as JsonObject
        val jwkString = Json.encodeToString(jwk)
        logger.debug("JWK: $jwkString")

        val key = agent.wallet.keyFactory.fromJwk(jwkString)
        val publicBytes = key.toPublicBytes()
        val signer = Base58.encode(publicBytes)

        val base64Payload = payload.encodeBase64url()
        val message = "${firstSig.protected}.$base64Payload".toByteArray()
        val isValid = key.verifySignature(message, signature, null)

        return Pair(isValid, signer)
    }
}
