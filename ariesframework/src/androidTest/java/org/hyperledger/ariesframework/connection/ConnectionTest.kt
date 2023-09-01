package org.hyperledger.ariesframework.connection

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.hyperledger.ariesframework.TestHelper
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.SubjectOutboundTransport
import org.hyperledger.ariesframework.connection.messages.ConnectionInvitationMessage
import org.hyperledger.ariesframework.connection.models.ConnectionState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class ConnectionTest {
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
    fun testMultiUseInvite() = runBlocking {
        val message = faberAgent.connections.createConnection(multiUseInvitation = true)
        val invitation = message.payload as ConnectionInvitationMessage
        val invitationUrl = invitation.toUrl("https://example.com")

        var aliceFaberConnection1 = aliceAgent.connections.receiveInvitationFromUrl(invitationUrl)
        delay(0.1.seconds)
        aliceFaberConnection1 = aliceAgent.connectionRepository.getById(aliceFaberConnection1.id)
        assertEquals(aliceFaberConnection1.state, ConnectionState.Complete)

        var aliceFaberConnection2 = aliceAgent.connections.receiveInvitationFromUrl(invitationUrl)
        delay(0.1.seconds)
        aliceFaberConnection2 = aliceAgent.connectionRepository.getById(aliceFaberConnection2.id)
        assertEquals(aliceFaberConnection2.state, ConnectionState.Complete)

        val faberAliceConnection1 = faberAgent.connectionService.getByThreadId(aliceFaberConnection1.threadId!!)
        val faberAliceConnection2 = faberAgent.connectionService.getByThreadId(aliceFaberConnection2.threadId!!)

        assertEquals(TestHelper.isConnectedWith(faberAliceConnection1, aliceFaberConnection1), true)
        assertEquals(TestHelper.isConnectedWith(faberAliceConnection2, aliceFaberConnection2), true)
    }
}
