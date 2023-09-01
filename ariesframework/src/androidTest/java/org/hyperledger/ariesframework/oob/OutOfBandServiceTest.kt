package org.hyperledger.ariesframework.oob

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.TestHelper
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.AgentConfig
import org.hyperledger.ariesframework.agent.AgentEvents
import org.hyperledger.ariesframework.connection.models.ConnectionState
import org.hyperledger.ariesframework.oob.messages.HandshakeReuseAcceptedMessage
import org.hyperledger.ariesframework.oob.messages.HandshakeReuseMessage
import org.hyperledger.ariesframework.oob.models.OutOfBandRole
import org.hyperledger.ariesframework.oob.models.OutOfBandState
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class OutOfBandServiceTest {
    lateinit var outOfBandService: OutOfBandService
    lateinit var agent: Agent
    lateinit var config: AgentConfig
    private val invitationId = "69212a3a-d068-4f9d-a2dd-4741bca89af3" // invitationId of the MockOutOfBand

    @Before
    fun setUp() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        config = TestHelper.getBaseConfig("alice")
        agent = Agent(context, config)
        agent.initialize()
        outOfBandService = agent.outOfBandService
    }

    @After
    fun tearDown() = runTest {
        agent.reset()
    }

    @Test
    fun testStateChange() = runTest {
        val reuseMessage = HandshakeReuseMessage(invitationId)
        val json = Json.encodeToString(reuseMessage)
        val messageContext = InboundMessageContext(
            reuseMessage,
            json,
            TestHelper.getMockConnection(),
        )

        var mockOob = TestHelper.getMockOutOfBand(
            OutOfBandRole.Sender,
            OutOfBandState.AwaitResponse,
            true,
        )
        agent.outOfBandRepository.save(mockOob)

        var eventCalled = false
        agent.eventBus.subscribe<AgentEvents.OutOfBandEvent> {
            eventCalled = true
        }
        outOfBandService.processHandshakeReuse(messageContext)
        assertFalse(eventCalled)

        mockOob.reusable = false
        agent.outOfBandRepository.update(mockOob)

        outOfBandService.processHandshakeReuse(messageContext)
        assertTrue(eventCalled)
    }

    @Test
    fun testHandshakeReuse() = runTest {
        val reuseMessage = HandshakeReuseMessage(invitationId)
        val json = Json.encodeToString(reuseMessage)
        val messageContext = InboundMessageContext(
            reuseMessage,
            json,
            TestHelper.getMockConnection(),
        )

        val mockOob = TestHelper.getMockOutOfBand(
            OutOfBandRole.Sender,
            OutOfBandState.AwaitResponse,
            true,
        )
        agent.outOfBandRepository.save(mockOob)

        val reuseAcceptedMessage = outOfBandService.processHandshakeReuse(messageContext)
        assertTrue(reuseAcceptedMessage.thread?.threadId == reuseMessage.id)
        assertTrue(reuseAcceptedMessage.thread?.parentThreadId == reuseMessage.thread?.parentThreadId)
    }

    @Test
    fun testHandshakeReuseAccepted() = runTest {
        val reuseAcceptedMessage = HandshakeReuseAcceptedMessage("threadId", invitationId)
        val json = Json.encodeToString(reuseAcceptedMessage)
        val connection = TestHelper.getMockConnection(ConnectionState.Complete)
        val messageContext = InboundMessageContext(
            reuseAcceptedMessage,
            json,
            connection,
        )

        val mockOob = TestHelper.getMockOutOfBand(
            OutOfBandRole.Receiver,
            OutOfBandState.PrepareResponse,
            true,
            connection.id,
        )
        agent.outOfBandRepository.save(mockOob)

        var eventCalled = false
        agent.eventBus.subscribe<AgentEvents.OutOfBandEvent> {
            eventCalled = true
            assertTrue(it.record.state == OutOfBandState.Done)
        }
        outOfBandService.processHandshakeReuseAccepted(messageContext)
        delay(0.1.seconds)
        assertTrue(eventCalled)
    }
}
