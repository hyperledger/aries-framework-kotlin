package org.hyperledger.ariesframework

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.hyperledger.ariesframework.proofs.models.RequestedCredentials
import org.hyperledger.ariesframework.util.concurrentForEach
import org.hyperledger.ariesframework.util.concurrentMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

@Serializable
data class DidDocument(
    val id: String,
    val service: List<Service>,
)

@Serializable
sealed class Service {
    abstract val id: String
}

interface DidComm {
    val id: String
    val serviceEndpoint: String
    val recipientKeys: List<String>
    val routingKeys: List<String>?
    val priority: Int?
}

@Serializable
@SerialName("DidDocumentService")
data class Service1(
    override val id: String,
    val serviceEndpoint: String,
) : Service()

@Serializable
@SerialName("IndyAgent")
data class Service2(
    override val id: String,
    override val serviceEndpoint: String,
    override val recipientKeys: List<String>,
    override val routingKeys: List<String>,
    override val priority: Int,
) : Service(), DidComm

@Serializable
@SerialName("did-communication")
data class Service3(
    override val id: String,
    val serviceEndpoint: String,
    val recipientKeys: List<String>,
    val routingKeys: List<String>,
    val priority: Int,
) : Service()

val module = SerializersModule {
    polymorphic(Service::class) {
        subclass(Service2::class)
        subclass(Service3::class)
        defaultDeserializer { Service1.serializer() }
    }
}

val testFormat = Json { serializersModule = module }

class SerializationTest {
    @Test
    fun parse_did_document() {
        val jsonString = """
        {
            "id": "12345",
            "service": [
                {
                    "id": "0",
                    "type": "Mediator public",
                    "serviceEndpoint": "did:sov:Q4zqM7aXqm7gDQkUVLng9h"
                },
                {
                    "id": "#6",
                    "type": "IndyAgent",
                    "serviceEndpoint": "did:sov:Q4zqM7aXqm7gDQkUVLng9h",
                    "recipientKeys": ["Q4zqM7aXqm7gDQkUVLng9h"],
                    "routingKeys": ["Q4zqM7aXqm7gDQkUVLng9h"],
                    "priority": 5
                },
                {
                    "id": "#7",
                    "type": "did-communication",
                    "serviceEndpoint": "https://agent.com/did-comm",
                    "recipientKeys": ["DADEajsDSaksLng9h"],
                    "routingKeys": ["DADEajsDSaksLng9h"],
                    "priority": 10
                }
            ]
        }
        """.trimIndent()

        // Decode JSON string to DidDocument object
        val didDocument = testFormat.decodeFromString<DidDocument>(jsonString)
        assertEquals("12345", didDocument.id)
        val decoded = Json { prettyPrint = true }.encodeToString(didDocument)
        println(decoded)

        val service = Json.decodeFromString<JsonObject>(Json.encodeToString(didDocument.service[0]))
        val type = service["type"]!!.jsonPrimitive.content
        assertNotEquals("Mediator public", type) // Original type is not preserved
        assertEquals("DidDocumentService", type)
    }

    @Test
    fun parse_oob() {
        @Serializable
        data class OutOfBand(
            val type: String,
            val did: String,
            val data: JsonObject,
        )

        @Serializable
        data class ReqMessage(
            val id: String,
            val type: String,
            val body: Map<String, String>,
        )

        val json = """
        {
            "type": "oob_message",
            "did": "example-did",
            "data": {
                "id": "12345",
                "type": "req_message",
                "body": {
                    "key1": "value1",
                    "key2": "value2"
                }
            }
        }
        """.trimIndent()

        val outOfBand = Json.decodeFromString<OutOfBand>(json)
        println("data: ${outOfBand.data}")

        if (outOfBand.data["type"]!!.jsonPrimitive.content == "req_message") {
            val reqMessage = Json.decodeFromString<ReqMessage>(outOfBand.data.toString())
            println(reqMessage)
        }
    }

    @Test
    fun testParallel() = runTest {
        val list = listOf(1, 2, 3, 4, 5)
        list.concurrentForEach {
            println(it)
        }
        val doubled = list.concurrentMap { it * 2 }
        println(doubled)
    }

    @Test
    fun testRequestedCredentials() {
        val credentials = RequestedCredentials()
        println(credentials.toJsonString())
    }
}
