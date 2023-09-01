package org.hyperledger.ariesframework.agent

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.agent.decorators.AttachmentData
import org.hyperledger.ariesframework.agent.decorators.JwsFlattenedFormat
import org.hyperledger.ariesframework.agent.decorators.JwsGeneralFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class AttachmentTest {
    @Test
    fun testDecodeAttachment() {
        val json = """
        {
            "@id": "ceffce22-6471-43e4-8945-b604091981c9",
            "description": "A small picture of a cat",
            "filename": "cat.png",
            "mime-type": "text/plain",
            "lastmod_time": "2001-01-01T00:00:00Z",
            "byte_count": 9200,
            "data": {
                "base64": "eyJIZWxsbyI6IndvcmxkIn0="
            }
        }
        """.trimIndent()

        val attachment = Json.decodeFromString<Attachment>(json)
        assertEquals("ceffce22-6471-43e4-8945-b604091981c9", attachment.id)
        assertEquals("A small picture of a cat", attachment.description)
        assertEquals("cat.png", attachment.filename)
        assertEquals("text/plain", attachment.mimetype)
        assertEquals("2001-01-01T00:00:00Z", attachment.lastModified.toString())
        assertEquals(9200, attachment.byteCount)
        assertEquals("eyJIZWxsbyI6IndvcmxkIn0=", attachment.data.base64)

        val encoded = Json.encodeToString(attachment)
        val encodedObject = Json.decodeFromString<JsonObject>(encoded)
        assertEquals("ceffce22-6471-43e4-8945-b604091981c9", encodedObject["@id"]?.jsonPrimitive?.content)
        assertEquals("A small picture of a cat", encodedObject["description"]?.jsonPrimitive?.content)
        assertEquals("cat.png", encodedObject["filename"]?.jsonPrimitive?.content)
        assertEquals("text/plain", encodedObject["mime-type"]?.jsonPrimitive?.content)
        assertEquals("2001-01-01T00:00:00Z", encodedObject["lastmod_time"]?.jsonPrimitive?.content)
        assertEquals(9200, encodedObject["byte_count"]?.jsonPrimitive?.int)
        assertEquals("eyJIZWxsbyI6IndvcmxkIn0=", encodedObject["data"]?.jsonObject?.get("base64")?.jsonPrimitive?.content)
    }

    @Test
    fun testAddJws() {
        val jwsA = JwsGeneralFormat(
            mapOf("kid" to "did:key:z6MkfD6ccYE22Y9pHKtixeczk92MmMi2oJCP6gmNooZVKB9A"),
            "OsDP4FM8792J9JlessA9IXv4YUYjIGcIAnPPrEJmgxYomMwDoH-h2DMAF5YF2VtsHHyhGN_0HryDjWSEAZdYBQ",
            "eyJhbGciOiJFZERTQSIsImp3ayI6eyJrdHkiOiJPS1AiLCJjcnYiOiJFZDI1NTE5IiwieCI6IkN6cmtiNjQ1MzdrVUVGRkN5SXI4STgxUWJJRGk2MnNrbU41Rm41LU1zVkUifX0", // ktlint-disable max-line-length
        )
        val jwsB = JwsGeneralFormat(
            mapOf("kid" to "did:key:z6MkvBpZTRb7tjuUF5AkmhG1JDV928hZbg5KAQJcogvhz9ax"),
            "eA3MPRpSTt5NR8EZkDNb849E9qfrlUm8-StWPA4kMp-qcH7oEc2-1En4fgpz_IWinEbVxCLbmKhWNyaTAuHNAg",
            "eyJhbGciOiJFZERTQSIsImtpZCI6ImRpZDprZXk6ejZNa3ZCcFpUUmI3dGp1VUY1QWttaEcxSkRWOTI4aFpiZzVLQVFKY29ndmh6OWF4IiwiandrIjp7Imt0eSI6Ik9LUCIsImNydiI6IkVkMjU1MTkiLCJ4IjoiNmNaMmJaS21LaVVpRjlNTEtDVjhJSVlJRXNPTEhzSkc1cUJKOVNyUVlCayIsImtpZCI6ImRpZDprZXk6ejZNa3ZCcFpUUmI3dGp1VUY1QWttaEcxSkRWOTI4aFpiZzVLQVFKY29ndmh6OWF4In19", // ktlint-disable max-line-length
        )

        val attachment = Attachment("some-uuid", data = AttachmentData("eyJIZWxsbyI6IndvcmxkIn0="))
        attachment.addJws(jwsA)
        when (val jws = attachment.data.jws) {
            is JwsGeneralFormat -> assertEquals(
                "did:key:z6MkfD6ccYE22Y9pHKtixeczk92MmMi2oJCP6gmNooZVKB9A",
                jws.header?.get("kid") ?: fail("Expected kid"),
            )
            else -> fail("Expected JwsGeneralFormat")
        }

        attachment.addJws(jwsB)
        when (val jws = attachment.data.jws) {
            is JwsFlattenedFormat -> {
                assertEquals(2, jws.signatures.size)
                assertEquals("did:key:z6MkfD6ccYE22Y9pHKtixeczk92MmMi2oJCP6gmNooZVKB9A", jws.signatures[0].header?.get("kid"))
                assertEquals("did:key:z6MkvBpZTRb7tjuUF5AkmhG1JDV928hZbg5KAQJcogvhz9ax", jws.signatures[1].header?.get("kid"))
            }
            else -> fail("Expected JwsFlattenedFormat")
        }
    }
}
