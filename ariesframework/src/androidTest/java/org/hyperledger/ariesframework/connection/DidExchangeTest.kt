package org.hyperledger.ariesframework.connection

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.hyperledger.ariesframework.TestHelper
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.SubjectOutboundTransport
import org.hyperledger.ariesframework.connection.models.ConnectionState
import org.hyperledger.ariesframework.oob.models.CreateOutOfBandInvitationConfig
import org.hyperledger.ariesframework.oob.models.HandshakeProtocol
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DidExchangeTest {
    lateinit var faberAgent: Agent
    lateinit var aliceAgent: Agent

    @Before
    fun setUp() = runTest {
        val faberConfig = TestHelper.getBaseConfig("faber")
        val aliceConfig = TestHelper.getBaseConfig("alice")

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        faberAgent = Agent(context, faberConfig)
        aliceAgent = Agent(context, aliceConfig)

        faberAgent.setOutboundTransport(SubjectOutboundTransport(aliceAgent))
        aliceAgent.setOutboundTransport(SubjectOutboundTransport(faberAgent))

        faberAgent.initialize()
        aliceAgent.initialize()
    }

    @After
    fun tearDown() = runTest {
        faberAgent.reset()
        aliceAgent.reset()
    }

    @Test
    fun testOobConnection() = runBlocking {
        val outOfBandRecord = faberAgent.oob.createInvitation(CreateOutOfBandInvitationConfig())
        val invitation = outOfBandRecord.outOfBandInvitation

        aliceAgent.agentConfig.preferredHandshakeProtocol = HandshakeProtocol.DidExchange11
        val (_, connection) = aliceAgent.oob.receiveInvitation(invitation)
        val aliceFaberConnection = connection
            ?: throw Exception("Connection is nil after receiving oob invitation")
        assertEquals(aliceFaberConnection.state, ConnectionState.Complete)

        val faberAliceConnection = faberAgent.connectionService.findByInvitationKey(invitation.invitationKey()!!)
            ?: throw Exception("Cannot find connection by invitation key")
        assertEquals(faberAliceConnection.state, ConnectionState.Complete)

        assertEquals(TestHelper.isConnectedWith(faberAliceConnection, aliceFaberConnection), true)
        assertEquals(TestHelper.isConnectedWith(aliceFaberConnection, faberAliceConnection), true)
    }
}
