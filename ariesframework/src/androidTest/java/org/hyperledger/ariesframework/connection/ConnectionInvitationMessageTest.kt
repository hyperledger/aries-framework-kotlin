package org.hyperledger.ariesframework.connection

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.hyperledger.ariesframework.agent.AgentMessage
import org.hyperledger.ariesframework.agent.MessageSerializer
import org.hyperledger.ariesframework.connection.messages.ConnectionInvitationMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ConnectionInvitationMessageTest {
    @Test
    fun testAgentMessageSerialize() {
        val json = """
          {
            "@type": "${ConnectionInvitationMessage.type}",
            "@id": "04a2c382-999e-4de9-a1d2-9dec0b2fa5e4",
            "recipientKeys": ["recipientKeyOne", "recipientKeyTwo"],
            "serviceEndpoint": "https://example.com",
            "routingKeys": [],
            "label": "test"
          }
        """

        val message1 = Json.decodeFromString<ConnectionInvitationMessage>(json)
        assert(message1 is ConnectionInvitationMessage)
        assertEquals("04a2c382-999e-4de9-a1d2-9dec0b2fa5e4", message1.id)
        assertEquals(ConnectionInvitationMessage.type, message1.type)
        println("message1: ${Json.encodeToString(message1)}")

        // When we don't know the type, we can use the MessageSerializer.
        // We need to register the serializer for the type first at some point.
        MessageSerializer.registerMessage(ConnectionInvitationMessage.type, ConnectionInvitationMessage::class)
        val message2 = MessageSerializer.decodeFromString(json)
        assert(message2 is ConnectionInvitationMessage)
        assertEquals("04a2c382-999e-4de9-a1d2-9dec0b2fa5e4", message2.id)
        assertEquals(ConnectionInvitationMessage.type, message2.type)
        println("message2: ${MessageSerializer.encodeToString(message2)}")

        val unknown = """
          {
            "@type": "unknown-message-type",
            "@id": "04a2c382-999e-4de9-a1d2-9dec0b2fa5e4",
            "recipientKeys": ["recipientKeyOne", "recipientKeyTwo"],
            "serviceEndpoint": "https://example.com",
            "routingKeys": [],
            "label": "test"
          }
        """
        val message3 = MessageSerializer.decodeFromString(unknown)
        assert(message3 is AgentMessage)
        assertEquals("04a2c382-999e-4de9-a1d2-9dec0b2fa5e4", message3.id)
        assertEquals("unknown-message-type", message3.type)
        println("message3: ${MessageSerializer.encodeToString(message3)}")
    }

    @Test
    fun testNoRoutingKey() {
        val json = """
          {
            "@type": "${ConnectionInvitationMessage.type}",
            "@id": "04a2c382-999e-4de9-a1d2-9dec0b2fa5e4",
            "recipientKeys": ["recipientKeyOne", "recipientKeyTwo"],
            "serviceEndpoint": "https://example.com",
            "label": "test"
          }
        """
        val invitation = Json.decodeFromString<ConnectionInvitationMessage>(json)
        assertNotNull("should allow routingKeys to be left out of inline invitation", invitation)
    }

    @Test
    fun testValidateKeys() {
        val json = """
          {
            "@type": "${ConnectionInvitationMessage.type}",
            "@id": "04a2c382-999e-4de9-a1d2-9dec0b2fa5e4",
            "label": "test"
          }
        """
        try {
            val invitation = Json.decodeFromString<ConnectionInvitationMessage>(json)
            // should throw error if both did and inline keys / endpoint are missing"
            assert(false)
        } catch (e: Exception) {
            // expected
        }
    }

    @Test
    fun testFromUrl() {
        val invitationUrl =
            "https://trinsic.studio/link/?d_m=eyJsYWJlbCI6InRlc3QiLCJpbWFnZVVybCI6Imh0dHBzOi8vdHJpbnNpY2FwaWFzc2V0cy5henVyZWVkZ2UubmV0L2ZpbGVzL2IyODhkMTE3LTNjMmMtNGFjNC05MzVhLWE1MDBkODQzYzFlOV9kMGYxN2I0OS0wNWQ5LTQ4ZDAtODJlMy1jNjg3MGI4MjNjMTUucG5nIiwic2VydmljZUVuZHBvaW50IjoiaHR0cHM6Ly9hcGkucG9ydGFsLnN0cmVldGNyZWQuaWQvYWdlbnQvTVZob1VaQjlHdUl6bVJzSTNIWUNuZHpBcXVKY1ZNdFUiLCJyb3V0aW5nS2V5cyI6WyJCaFZRdEZHdGJ4NzZhMm13Y3RQVkJuZWtLaG1iMTdtUHdFMktXWlVYTDFNaSJdLCJyZWNpcGllbnRLZXlzIjpbIkcyOVF6bXBlVXN0dUVHYzlXNzlYNnV2aUhTUTR6UlV2VWFFOHpXV2VZYjduIl0sIkBpZCI6IjgxYzZiNDUzLWNkMTUtNDQwMC04MWU5LTkwZTJjM2NhY2I1NCIsIkB0eXBlIjoiZGlkOnNvdjpCekNic05ZaE1yakhpcVpEVFVBU0hnO3NwZWMvY29ubmVjdGlvbnMvMS4wL2ludml0YXRpb24ifQ%3D%3D" // ktlint-disable max-line-length
        val invitation = ConnectionInvitationMessage.fromUrl(invitationUrl)
        assertNotNull(
            "should correctly convert a valid invitation url to a `ConnectionInvitationMessage` with `d_m` as parameter",
            invitation,
        )
    }

    @Test
    fun testFromUrlCI() {
        val invitationUrl =
            "https://example.com?c_i=eyJAdHlwZSI6ICJkaWQ6c292OkJ6Q2JzTlloTXJqSGlxWkRUVUFTSGc7c3BlYy9jb25uZWN0aW9ucy8xLjAvaW52aXRhdGlvbiIsICJAaWQiOiAiZmM3ODFlMDItMjA1YS00NGUzLWE5ZTQtYjU1Y2U0OTE5YmVmIiwgInNlcnZpY2VFbmRwb2ludCI6ICJodHRwczovL2RpZGNvbW0uZmFiZXIuYWdlbnQuYW5pbW8uaWQiLCAibGFiZWwiOiAiQW5pbW8gRmFiZXIgQWdlbnQiLCAicmVjaXBpZW50S2V5cyI6IFsiR0hGczFQdFRabjdmYU5LRGVnMUFzU3B6QVAyQmpVckVjZlR2bjc3SnBRTUQiXX0=" // ktlint-disable max-line-length
        val invitation = ConnectionInvitationMessage.fromUrl(invitationUrl)
        assertNotNull(
            "should correctly convert a valid invitation url to a `ConnectionInvitationMessage` with `c_i` as parameter",
            invitation,
        )
    }

    @Test
    fun testFromUrlCINoBase64Padding() {
        val invitationUrl =
            "https://example.com?c_i=eyJAdHlwZSI6ICJkaWQ6c292OkJ6Q2JzTlloTXJqSGlxWkRUVUFTSGc7c3BlYy9jb25uZWN0aW9ucy8xLjAvaW52aXRhdGlvbiIsICJAaWQiOiAiZmM3ODFlMDItMjA1YS00NGUzLWE5ZTQtYjU1Y2U0OTE5YmVmIiwgInNlcnZpY2VFbmRwb2ludCI6ICJodHRwczovL2RpZGNvbW0uZmFiZXIuYWdlbnQuYW5pbW8uaWQiLCAibGFiZWwiOiAiQW5pbW8gRmFiZXIgQWdlbnQiLCAicmVjaXBpZW50S2V5cyI6IFsiR0hGczFQdFRabjdmYU5LRGVnMUFzU3B6QVAyQmpVckVjZlR2bjc3SnBRTUQiXX0" // ktlint-disable max-line-length
        val invitation = ConnectionInvitationMessage.fromUrl(invitationUrl)
        assertNotNull(
            "should correctly convert a valid invitation url to a `ConnectionInvitationMessage` with `c_i` as parameter",
            invitation,
        )
    }

    @Test
    fun testToUrl() {
        val invitationUrl =
            "https://example.com?c_i=eyJAdHlwZSI6ICJkaWQ6c292OkJ6Q2JzTlloTXJqSGlxWkRUVUFTSGc7c3BlYy9jb25uZWN0aW9ucy8xLjAvaW52aXRhdGlvbiIsICJAaWQiOiAiZmM3ODFlMDItMjA1YS00NGUzLWE5ZTQtYjU1Y2U0OTE5YmVmIiwgInNlcnZpY2VFbmRwb2ludCI6ICJodHRwczovL2RpZGNvbW0uZmFiZXIuYWdlbnQuYW5pbW8uaWQiLCAibGFiZWwiOiAiQW5pbW8gRmFiZXIgQWdlbnQiLCAicmVjaXBpZW50S2V5cyI6IFsiR0hGczFQdFRabjdmYU5LRGVnMUFzU3B6QVAyQmpVckVjZlR2bjc3SnBRTUQiXX0=" // ktlint-disable max-line-length
        val invitation = ConnectionInvitationMessage.fromUrl(invitationUrl)
        val url = invitation.toUrl("https://example.com")
        val invitation1 = ConnectionInvitationMessage.fromUrl(url)
        assertEquals(invitation.label, invitation1.label)
        assertEquals(invitation.serviceEndpoint, invitation1.serviceEndpoint)
    }
}
