package org.hyperledger.ariesframework.storage

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.hyperledger.ariesframework.TestHelper
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.Dispatcher
import org.hyperledger.ariesframework.connection.messages.ConnectionInvitationMessage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DidCommMessageRepositoryTest {
    lateinit var agent: Agent
    lateinit var repository: DidCommMessageRepository
    lateinit var invitation: ConnectionInvitationMessage

    @Before
    fun setUp() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = TestHelper.getBaseConfig()
        agent = Agent(context, config)
        repository = agent.didCommMessageRepository
        agent.initialize()
        invitation = ConnectionInvitationMessage(
            label = "test",
            recipientKeys = listOf("recipientKeyOne", "recipientKeyTwo"),
            serviceEndpoint = "https://example.com",
        )
    }

    @After
    fun tearDown() = runTest {
        agent.reset()
    }

    private fun getRecord(): DidCommMessageRecord {
        return DidCommMessageRecord(invitation, DidCommMessageRole.Receiver, "04a2c382-999e-4de9-a1d2-9dec0b2fa5e4")
    }

    @Test
    fun testGetAgentMessage() = runTest {
        val record = getRecord()
        repository.saveAgentMessage(DidCommMessageRole.Receiver, invitation, record.associatedRecordId!!)

        val message = repository.getAgentMessage(record.associatedRecordId!!, ConnectionInvitationMessage.type)
        val decoded = Json.decodeFromString<ConnectionInvitationMessage>(message)

        assertEquals(decoded.id, invitation.id)
        assertEquals(decoded.label, invitation.label)
        assertEquals(decoded.serviceEndpoint, invitation.serviceEndpoint)
    }

    @Test
    fun testFindAgentMessage() = runTest {
        val record = getRecord()
        repository.saveAgentMessage(DidCommMessageRole.Receiver, invitation, record.associatedRecordId!!)

        val message = repository.findAgentMessage(record.associatedRecordId!!, ConnectionInvitationMessage.type)
        val decoded = Json.decodeFromString<ConnectionInvitationMessage>(message!!)

        assertEquals(decoded.id, invitation.id)
        assertEquals(decoded.label, invitation.label)
        assertEquals(decoded.serviceEndpoint, invitation.serviceEndpoint)

        val notFound = repository.findAgentMessage("non-found", ConnectionInvitationMessage.type)
        assertEquals(notFound, null)
    }

    @Test
    fun testSaveAgentMessage() = runTest {
        val record = getRecord()
        repository.saveAgentMessage(DidCommMessageRole.Receiver, invitation, record.associatedRecordId!!)

        val message = repository.getAgentMessage(record.associatedRecordId!!, ConnectionInvitationMessage.type)
        val decoded = Json.decodeFromString<ConnectionInvitationMessage>(message)

        assertEquals(decoded.id, invitation.id)
        assertEquals(decoded.label, invitation.label)
        assertEquals(decoded.serviceEndpoint, invitation.serviceEndpoint)

        val invitationUpdate = ConnectionInvitationMessage(
            label = "test-update",
            recipientKeys = listOf("recipientKeyOne", "recipientKeyTwo"),
            serviceEndpoint = "https://example.com",
        )

        repository.saveOrUpdateAgentMessage(DidCommMessageRole.Sender, invitationUpdate, record.associatedRecordId!!)
        val updatedMessage = repository.getAgentMessage(record.associatedRecordId!!, ConnectionInvitationMessage.type)
        val decodedUpdate = Json.decodeFromString<ConnectionInvitationMessage>(updatedMessage)

        assertEquals(decodedUpdate.id, invitationUpdate.id)
        assertEquals(decodedUpdate.label, invitationUpdate.label)

        var type = ConnectionInvitationMessage.type
        if (agent.agentConfig.useLegacyDidSovPrefix) {
            type = Dispatcher.replaceNewDidCommPrefixWithLegacyDidSov(type)
        }
        val updatedRecord = repository.findSingleByQuery(
            """
            {"associatedRecordId": "${record.associatedRecordId!!}",
            "messageType": "$type"}
            """,
        )!!

        assertEquals(updatedRecord.message, invitationUpdate.toJsonString())
        assertEquals(updatedRecord.role, DidCommMessageRole.Sender)
    }
}
