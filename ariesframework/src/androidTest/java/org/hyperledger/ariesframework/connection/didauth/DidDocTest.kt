package org.hyperledger.ariesframework.connection.didauth

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.* // ktlint-disable
import org.hyperledger.ariesframework.connection.models.didauth.* // ktlint-disable
import org.hyperledger.ariesframework.connection.models.didauth.publicKey.* // ktlint-disable
import org.junit.Assert.assertEquals
import org.junit.Test

class DidDocTest {
    val diddoc = """
        {
            "@context": "https://w3id.org/did/v1",
            "id": "did:sov:LjgpST2rjsoxYegQDRm7EL",
            "publicKey": [
                {
                    "id": "3",
                    "type": "RsaVerificationKey2018",
                    "controller": "did:sov:LjgpST2rjsoxYegQDRm7EL",
                    "publicKeyPem": "-----BEGIN PUBLIC X..."
                },
                {
                    "id": "did:sov:LjgpST2rjsoxYegQDRm7EL#4",
                    "type": "Ed25519VerificationKey2018",
                    "controller": "did:sov:LjgpST2rjsoxYegQDRm7EL",
                    "publicKeyBase58": "-----BEGIN PUBLIC 9..."
                },
                {
                    "id": "6",
                    "type": "Secp256k1VerificationKey2018",
                    "controller": "did:sov:LjgpST2rjsoxYegQDRm7EL",
                    "publicKeyHex": "-----BEGIN PUBLIC A..."
                }
            ],
            "service": [
                {
                    "id": "0",
                    "type": "Mediator",
                    "serviceEndpoint": "did:sov:Q4zqM7aXqm7gDQkUVLng9h"
                },
                {
                    "id": "6",
                    "type": "IndyAgent",
                    "serviceEndpoint": "did:sov:Q4zqM7aXqm7gDQkUVLng9h",
                    "recipientKeys": ["Q4zqM7aXqm7gDQkUVLng9h"],
                    "routingKeys": ["Q4zqM7aXqm7gDQkUVLng9h"],
                    "priority": 5
                },
                {
                    "id": "7",
                    "type": "did-communication",
                    "serviceEndpoint": "https://agent.com/did-comm",
                    "recipientKeys": ["DADEajsDSaksLng9h"],
                    "routingKeys": ["DADEajsDSaksLng9h"],
                    "priority": 10
                },
                {
                    "id": "did:example:123456789abcdefghi#didcomm-1",
                    "type": "DIDCommMessaging",
                    "serviceEndpoint": {
                        "uri": "https://example.com/path",
                        "accept": [
                            "didcomm/v2",
                            "didcomm/aip2;env=rfc587"
                        ],
                        "routingKeys": ["did:example:somemediator#somekey"]
                    }
                }
            ],
            "authentication": [
                {
                    "type": "RsaSignatureAuthentication2018",
                    "publicKey": "3"
                },
                {
                    "id": "6",
                    "type": "RsaVerificationKey2018",
                    "controller": "did:sov:LjgpST2rjsoxYegQDRm7EL",
                    "publicKeyPem": "-----BEGIN PUBLIC A..."
                }
            ]
        }    
    """.trimIndent()

    @Test
    fun testJsonCoding() {
        val didDocJson = Json.decodeFromString<JsonObject>(diddoc)
        val didDoc = Json { serializersModule = didDocServiceModule }.decodeFromString<DidDoc>(diddoc)

        assertEquals(didDocJson["publicKey"]!!.jsonArray.size, didDoc.publicKey.size)
        assertEquals(didDocJson["service"]!!.jsonArray.size, didDoc.service.size)
        assertEquals(didDocJson["authentication"]!!.jsonArray.size, didDoc.authentication.size)

        assertEquals(didDocJson["id"]!!.jsonPrimitive.content, didDoc.id)
        assertEquals(didDocJson["@context"]!!.jsonPrimitive.content, didDoc.context)

        assert(didDoc.publicKey[0] is RsaSig2018)
        assert(didDoc.publicKey[1] is Ed25119Sig2018)
        assert(didDoc.publicKey[2] is EddsaSaSigSecp256k1)

        assertEquals("-----BEGIN PUBLIC X...", didDoc.publicKey[0].value)
        assertEquals("-----BEGIN PUBLIC 9...", didDoc.publicKey[1].value)
        assertEquals("-----BEGIN PUBLIC A...", didDoc.publicKey[2].value)

        assert(didDoc.service[0] is DidDocumentService)
        assert(didDoc.service[1] is IndyAgentService)
        assert(didDoc.service[2] is DidCommService)
        assert(didDoc.service[3] is DidCommV2Service)

        assert(didDoc.authentication[0] is ReferencedAuthentication)
        assert(didDoc.authentication[1] is EmbeddedAuthentication)

        val encoded = Json { serializersModule = didDocServiceModule; prettyPrint = true }.encodeToString(didDoc)
        val encodedJson = Json.decodeFromString<JsonObject>(encoded)
        println("Encoded json:\n$encoded")

        assertEquals("did:sov:LjgpST2rjsoxYegQDRm7EL", encodedJson["id"]!!.jsonPrimitive.content)
        assertEquals("https://w3id.org/did/v1", encodedJson["@context"]!!.jsonPrimitive.content)
        assertEquals(3, encodedJson["publicKey"]!!.jsonArray.size)
        assertEquals(4, encodedJson["service"]!!.jsonArray.size)
        assertEquals(2, encodedJson["authentication"]!!.jsonArray.size)

        assertEquals("3", encodedJson["publicKey"]!!.jsonArray[0].jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals("RsaVerificationKey2018", encodedJson["publicKey"]!!.jsonArray[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(
            "did:sov:LjgpST2rjsoxYegQDRm7EL",
            encodedJson["publicKey"]!!.jsonArray[0].jsonObject["controller"]!!.jsonPrimitive.content,
        )
        assertEquals("-----BEGIN PUBLIC X...", encodedJson["publicKey"]!!.jsonArray[0].jsonObject["publicKeyPem"]!!.jsonPrimitive.content)

        assertEquals("0", encodedJson["service"]!!.jsonArray[0].jsonObject["id"]!!.jsonPrimitive.content)
        // type is not preserved
        // assertEquals("Mediator", encodedJson["service"]!!.jsonArray[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(
            "did:sov:Q4zqM7aXqm7gDQkUVLng9h",
            encodedJson["service"]!!.jsonArray[0].jsonObject["serviceEndpoint"]!!.jsonPrimitive.content,
        )

        assertEquals("6", encodedJson["service"]!!.jsonArray[1].jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals("IndyAgent", encodedJson["service"]!!.jsonArray[1].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(
            "did:sov:Q4zqM7aXqm7gDQkUVLng9h",
            encodedJson["service"]!!.jsonArray[1].jsonObject["serviceEndpoint"]!!.jsonPrimitive.content,
        )
        assertEquals(
            "Q4zqM7aXqm7gDQkUVLng9h",
            encodedJson["service"]!!.jsonArray[1].jsonObject["recipientKeys"]!!.jsonArray[0].jsonPrimitive.content,
        )
        assertEquals(
            "Q4zqM7aXqm7gDQkUVLng9h",
            encodedJson["service"]!!.jsonArray[1].jsonObject["routingKeys"]!!.jsonArray[0].jsonPrimitive.content,
        )
        assertEquals(5, encodedJson["service"]!!.jsonArray[1].jsonObject["priority"]!!.jsonPrimitive.int)
    }

    @Test
    fun testGetPublicKey() {
        val didDoc = Json { serializersModule = didDocServiceModule }.decodeFromString<DidDoc>(diddoc)
        val publicKey = didDoc.publicKey("3")
        assertEquals("3", publicKey?.id)
    }

    @Test
    fun testGetServicesByType() {
        val didDoc = Json { serializersModule = didDocServiceModule }.decodeFromString<DidDoc>(diddoc)
        val services = didDoc.servicesByType<IndyAgentService>()
        assertEquals(1, services.size)
        assertEquals("6", services[0].id)
    }

    @Test
    fun testGetDidCommServices() {
        val didDoc = Json { serializersModule = didDocServiceModule }.decodeFromString<DidDoc>(diddoc)
        val services = didDoc.didCommServices()
        assertEquals(2, services.size)
        assert(services[0] is DidCommService)
        assert(services[1] is IndyAgentService)
    }
}
