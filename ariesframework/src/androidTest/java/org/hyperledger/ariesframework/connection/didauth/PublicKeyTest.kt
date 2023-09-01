package org.hyperledger.ariesframework.connection.didauth

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.hyperledger.ariesframework.connection.models.didauth.publicKey.PublicKey
import org.junit.Assert.assertEquals
import org.junit.Test

typealias PublicKeyObjects = Array<JsonObject>

class PublicKeyTest {
    val json = """
        [{
            "valueKey": "publicKeyPem",
            "json": {
                "id": "3",
                "type": "RsaVerificationKey2018",
                "controller": "did:sov:LjgpST2rjsoxYegQDRm7EL",
                "publicKeyPem": "-----BEGIN PUBLIC X..."
            }
        },
        {
            "valueKey": "publicKeyBase58",
            "json": {
                "id": "4",
                "type": "Ed25519VerificationKey2018",
                "controller": "did:sov:LjgpST2rjsoxYegQDRm7EL",
                "publicKeyBase58": "-----BEGIN PUBLIC X..."
            }
        },
        {
            "valueKey": "publicKeyHex",
            "json": {
                "id": "did:sov:LjgpST2rjsoxYegQDRm7EL#5",
                "type": "Secp256k1VerificationKey2018",
                "controller": "did:sov:LjgpST2rjsoxYegQDRm7EL",
                "publicKeyHex": "-----BEGIN PUBLIC X..."
            }
        }]
    """.trimIndent()

    @Test
    fun testPublicKeyCoding() {
        val publicKeys = Json.decodeFromString<PublicKeyObjects>(json)
        for (item in publicKeys) {
            val pubKeyValueKey = item["valueKey"]!!.jsonPrimitive.content
            val pubKeyJson = item["json"]!!.jsonObject
            val json = pubKeyJson.toString()

            val pubKey = Json.decodeFromString<PublicKey>(json)
            assertEquals(pubKey.id, pubKeyJson["id"]!!.jsonPrimitive.content)
            assertEquals(pubKey.controller, pubKeyJson["controller"]!!.jsonPrimitive.content)
            assertEquals(pubKey.value, pubKeyJson[pubKeyValueKey]!!.jsonPrimitive.content)

            val encoded = Json.encodeToString(pubKey)
            val clone = Json.decodeFromString<PublicKey>(encoded)
            assertEquals(pubKey.id, clone.id)
            assertEquals(pubKey.controller, clone.controller)
            assertEquals(pubKey.value, clone.value)
        }
    }
}
