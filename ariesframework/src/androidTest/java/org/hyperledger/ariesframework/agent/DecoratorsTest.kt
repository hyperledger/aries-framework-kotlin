package org.hyperledger.ariesframework.agent

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.hyperledger.ariesframework.connection.messages.ConnectionInvitationMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class DecoratorsTest {
    @Test
    fun testDecoratorParse() {
        val json = """
          {
            "@type": "${ConnectionInvitationMessage.type}",
            "@id": "04a2c382-999e-4de9-a1d2-9dec0b2fa5e4",
            "~thread": {
              "thid": "ceffce22-6471-43e4-8945-b604091981c9",
              "pthid": "917a109d-eae3-42bc-9436-b02426d3ce2c",
              "sender_order": 1,
              "received_orders": {
                "did:sov:123456789abcdefghi1234": 1
              }
            },
            "~transport": {
              "return_route": "all"
            },
            "recipientKeys": ["recipientKeyOne", "recipientKeyTwo"],
            "serviceEndpoint": "https://example.com",
            "routingKeys": [],
            "label": "test"
          }
        """

        val message = Json.decodeFromString<ConnectionInvitationMessage>(json)
        assertEquals("04a2c382-999e-4de9-a1d2-9dec0b2fa5e4", message.id)
        assertEquals("ceffce22-6471-43e4-8945-b604091981c9", message.thread?.threadId)
        assertEquals("917a109d-eae3-42bc-9436-b02426d3ce2c", message.thread?.parentThreadId)
        assertEquals("all", message.transport?.returnRoute)
    }
}
