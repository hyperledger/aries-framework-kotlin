package org.hyperledger.ariesframework.oob

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.hyperledger.ariesframework.TestHelper
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.SubjectOutboundTransport
import org.hyperledger.ariesframework.connection.messages.TrustPingMessage
import org.hyperledger.ariesframework.connection.models.ConnectionState
import org.hyperledger.ariesframework.oob.models.CreateOutOfBandInvitationConfig
import org.hyperledger.ariesframework.oob.models.HandshakeProtocol
import org.hyperledger.ariesframework.oob.models.OutOfBandRole
import org.hyperledger.ariesframework.oob.models.OutOfBandState
import org.hyperledger.ariesframework.oob.models.ReceiveOutOfBandInvitationConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class OobTest {
    lateinit var faberAgent: Agent
    lateinit var aliceAgent: Agent
    val makeConnectionConfig = CreateOutOfBandInvitationConfig(
        label = "Faber College",
        goalCode = "p2p-messaging",
        goal = "To make a connection",
    )
    val receiveInvitationConfig = ReceiveOutOfBandInvitationConfig(
        autoAcceptConnection = true,
    )

    @Before
    fun setUp() = runTest {
        prepareAgents(false)
    }

    @After
    fun tearDown() = runTest {
        faberAgent.reset()
        aliceAgent.reset()
    }

    private suspend fun prepareAgents(useLedgerService: Boolean) {
        val faberConfig = TestHelper.getBaseConfig("faber", useLedgerService)
        faberAgent = Agent(InstrumentationRegistry.getInstrumentation().targetContext, faberConfig)
        faberAgent.initialize()

        val aliceConfig = TestHelper.getBaseConfig("alice", useLedgerService)
        aliceAgent = Agent(InstrumentationRegistry.getInstrumentation().targetContext, aliceConfig)
        aliceAgent.initialize()

        faberAgent.setOutboundTransport(SubjectOutboundTransport(aliceAgent))
        aliceAgent.setOutboundTransport(SubjectOutboundTransport(faberAgent))
    }

    @Test
    fun testCreateOutOfBandInvitation() = runTest {
        val outOfBandRecord = faberAgent.oob.createInvitation(makeConnectionConfig)

        assertEquals(outOfBandRecord.autoAcceptConnection, true)
        assertEquals(outOfBandRecord.role, OutOfBandRole.Sender)
        assertEquals(outOfBandRecord.state, OutOfBandState.AwaitResponse)
        assertEquals(outOfBandRecord.reusable, false)
        assertEquals(outOfBandRecord.outOfBandInvitation.goal, makeConnectionConfig.goal)
        assertEquals(outOfBandRecord.outOfBandInvitation.goalCode, makeConnectionConfig.goalCode)
        assertEquals(outOfBandRecord.outOfBandInvitation.label, makeConnectionConfig.label)
    }

    @Test
    fun testCreateWithHandshakeAndRequests() = runTest {
        val message = TrustPingMessage(comment = "Hello")
        val config = CreateOutOfBandInvitationConfig(
            label = "test-connection",
            messages = listOf(message),
        )
        val outOfBandRecord = faberAgent.oob.createInvitation(config)
        val invitation = outOfBandRecord.outOfBandInvitation

        assertTrue(invitation.handshakeProtocols!!.contains(HandshakeProtocol.Connections))
        assertEquals(invitation.getRequestsJson().size, 1)
    }

    @Test
    fun testCreateWithOfferCredentialMessage() = runTest {
        // TODO: Add test
    }

    @Test
    fun testReceiveInvitation() = runTest {
        val outOfBandRecord = faberAgent.oob.createInvitation(makeConnectionConfig)
        val invitation = outOfBandRecord.outOfBandInvitation

        val (receivedOutOfBandRecord, _) = aliceAgent.oob.receiveInvitation(invitation)

        assertEquals(receivedOutOfBandRecord.role, OutOfBandRole.Receiver)
        assertEquals(receivedOutOfBandRecord.state, OutOfBandState.Done)
        assertEquals(receivedOutOfBandRecord.outOfBandInvitation.goal, makeConnectionConfig.goal)
        assertEquals(receivedOutOfBandRecord.outOfBandInvitation.goalCode, makeConnectionConfig.goalCode)
        assertEquals(receivedOutOfBandRecord.outOfBandInvitation.label, makeConnectionConfig.label)
    }

    @Test
    fun testConnectionWithURL() = runTest {
        val outOfBandRecord = faberAgent.oob.createInvitation(makeConnectionConfig)
        val invitation = outOfBandRecord.outOfBandInvitation
        val url = invitation.toUrl("http://example.com")

        val (_, aliceFaberConnection) = aliceAgent.oob.receiveInvitationFromUrl(url)
        assertNotNull(aliceFaberConnection)

        val faberAliceConnection = faberAgent.connectionService.findByInvitationKey(invitation.invitationKey()!!)
        assertNotNull(faberAliceConnection)

        if (aliceFaberConnection != null && faberAliceConnection != null) {
            assertEquals(aliceFaberConnection.state, ConnectionState.Complete)
            assertEquals(faberAliceConnection.state, ConnectionState.Complete)
            assertEquals(faberAliceConnection.alias, makeConnectionConfig.alias)
            assertTrue(TestHelper.isConnectedWith(faberAliceConnection, aliceFaberConnection))
            assertTrue(TestHelper.isConnectedWith(aliceFaberConnection, faberAliceConnection))
        }
    }

    @Test
    fun testCredentialOffer() = runTest {
        // TODO: Add test
    }

    @Test
    fun testWithHandshakeReuse() = runTest {
        val routing = faberAgent.mediationRecipient.getRouting()
        val outOfBandRecord = faberAgent.oob.createInvitation(CreateOutOfBandInvitationConfig(routing = routing))
        val (_, firstAliceFaberConnection) = aliceAgent.oob.receiveInvitation(outOfBandRecord.outOfBandInvitation)

        val outOfBandRecord2 = faberAgent.oob.createInvitation(CreateOutOfBandInvitationConfig(routing = routing))
        val (_, secondAliceFaberConnection) = aliceAgent.oob.receiveInvitation(
            outOfBandRecord2.outOfBandInvitation,
            ReceiveOutOfBandInvitationConfig(reuseConnection = true),
        )

        assertEquals(firstAliceFaberConnection!!.id, secondAliceFaberConnection!!.id)

        val faberConnections = faberAgent.connectionRepository.getAll()
        assertEquals(faberConnections.size, 1)
    }

    @Test
    fun testWithoutHandshakeReuse() = runTest {
        val routing = faberAgent.mediationRecipient.getRouting()
        val outOfBandRecord = faberAgent.oob.createInvitation(CreateOutOfBandInvitationConfig(routing = routing))
        val (_, firstAliceFaberConnection) = aliceAgent.oob.receiveInvitation(outOfBandRecord.outOfBandInvitation)

        val outOfBandRecord2 = faberAgent.oob.createInvitation(CreateOutOfBandInvitationConfig(routing = routing))
        val (_, secondAliceFaberConnection) = aliceAgent.oob.receiveInvitation(
            outOfBandRecord2.outOfBandInvitation,
            ReceiveOutOfBandInvitationConfig(reuseConnection = false),
        )

        assertNotEquals(firstAliceFaberConnection!!.id, secondAliceFaberConnection!!.id)

        val faberConnections = faberAgent.connectionRepository.getAll()
        assertEquals(faberConnections.size, 2)
    }

    @Test
    fun testReceivingSameInvitation() = runTest {
        val outOfBandRecord = faberAgent.oob.createInvitation(makeConnectionConfig)
        val invitation = outOfBandRecord.outOfBandInvitation

        val (_, firstAliceFaberConnection) = aliceAgent.oob.receiveInvitation(invitation)
        assertNotNull(firstAliceFaberConnection)

        try {
            aliceAgent.oob.receiveInvitation(invitation)
            fail("Should not be able to receive same invitation twice")
        } catch (e: Exception) {
            // expected
        }
    }
}
