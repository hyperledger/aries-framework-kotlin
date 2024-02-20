package org.hyperledger.ariesframework.connection

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.hyperledger.ariesframework.TestHelper
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.AgentConfig
import org.hyperledger.ariesframework.connection.messages.ConnectionInvitationMessage
import org.hyperledger.ariesframework.connection.messages.ConnectionRequestMessage
import org.hyperledger.ariesframework.connection.models.ConnectionRole
import org.hyperledger.ariesframework.connection.models.ConnectionState
import org.hyperledger.ariesframework.connection.models.didauth.didDocServiceModule
import org.hyperledger.ariesframework.routing.Routing
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

const val connectionImageUrl = "https://example.com/image.png"

class ConnectionServiceTest {
    lateinit var routing: Routing
    lateinit var connectionService: ConnectionService
    lateinit var agent: Agent
    lateinit var config: AgentConfig

    @Before
    fun setUp() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val key = "HfyxAyKK8Z2xVzWbXXy2erY32B9Bnr8WFgR5HfzjAnGx"
        config = AgentConfig(
            walletId = "wallet_id",
            walletKey = key,
            genesisPath = "",
            poolName = "pool_id",
            mediatorConnectionsInvite = null,
            label = "Default Agent",
            autoAcceptConnections = true,
            connectionImageUrl = connectionImageUrl,
            useLedgerService = false,
        )

        routing = Routing(
            config.endpoints,
            "fakeVerkey",
            "fakeDid",
            emptyList(),
            "fakeMediatorId",
        )

        agent = Agent(context, config)
        agent.initialize()
        connectionService = agent.connectionService
    }

    @After
    fun tearDown() = runTest {
        agent.reset()
    }

    @Test
    fun testProcessInvitation() = runTest {
        val recipientKey = "key-1"
        val invitation = ConnectionInvitationMessage(
            label = "test label",
            imageUrl = connectionImageUrl,
            recipientKeys = listOf(recipientKey),
            serviceEndpoint = "https://test.com/msg",
        )

        val connection = connectionService.processInvitation(invitation, null, routing)
        val connectionAlias = connectionService.processInvitation(invitation, null, routing, null, "test-alias")

        assertEquals(connection.role, ConnectionRole.Invitee)
        assertEquals(connection.state, ConnectionState.Invited)
        assertNull(connection.autoAcceptConnection)
        assertNotNull(connection.id)
        assertNotNull(connection.verkey)
        assertEquals(connection.mediatorId, "fakeMediatorId")

        val tags = connection.getTags()
        assertEquals(tags["verkey"], connection.verkey)
        assertEquals(tags["invitationKey"], recipientKey)

        assertNull(connection.alias)
        assertEquals(connectionAlias.alias, "test-alias")
        assertEquals(connection.theirLabel, "test label")
        assertEquals(connection.imageUrl, connectionImageUrl)
    }

    @Test
    fun testCreateRequest() = runTest {
        val connection = TestHelper.getMockConnection()
        connectionService.connectionRepository.save(connection)

        val outboundMessage = connectionService.createRequest(connection.id)
        val message = outboundMessage.payload as ConnectionRequestMessage

        assertEquals(outboundMessage.connection.state, ConnectionState.Requested)
        assertEquals(message.label, config.label)
        assertEquals(message.connection.did, "test-did")

        assertEquals(
            Json { serializersModule = didDocServiceModule }.encodeToString(message.connection.didDoc),
            Json { serializersModule = didDocServiceModule }.encodeToString(connection.didDoc),
        )
        assertEquals(message.imageUrl, connectionImageUrl)
    }
}
