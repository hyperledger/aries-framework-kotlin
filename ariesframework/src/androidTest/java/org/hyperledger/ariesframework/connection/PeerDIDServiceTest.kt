package org.hyperledger.ariesframework.connection

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.hyperledger.ariesframework.TestHelper
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.connection.models.didauth.DidComm
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PeerDIDServiceTest {
    lateinit var agent: Agent
    val verkey = "3uhKmLCRYfe5YWDsgBC4VNTKk3RbnFCzgjVH3zmSKHWa"

    @Before
    fun setUp() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = TestHelper.getBaseConfig()
        agent = Agent(context, config)
        agent.initialize()
    }

    @After
    fun tearDown() = runTest {
        agent.reset()
    }

    @Test
    fun testPeerDIDwithLegacyService() = runTest {
        val peerDID = agent.peerDIDService.createPeerDID(verkey)
        parsePeerDID(peerDID)
    }

    @Test
    fun testPeerDIDwithDidCommV2Service() = runTest {
        val peerDID = agent.peerDIDService.createPeerDID(verkey, useLegacyService = false)
        parsePeerDID(peerDID)
    }

    suspend fun parsePeerDID(peerDID: String) {
        assertTrue(peerDID.startsWith("did:peer:2"))

        val didDoc = agent.peerDIDService.parsePeerDID(peerDID)
        assertEquals(didDoc.id, peerDID)
        assertEquals(didDoc.publicKey.size, 1)
        assertEquals(didDoc.service.size, 1)
        assertEquals(didDoc.authentication.size, 1)
        assertEquals(didDoc.publicKey[0].value, verkey)

        val service = didDoc.service.first()
        assertTrue(service is DidComm)
        val didCommService = service as DidComm
        assertEquals(didCommService.recipientKeys.size, 1)
        assertEquals(didCommService.recipientKeys[0], verkey)
        assertEquals(didCommService.routingKeys?.size, 0)
        assertEquals(didCommService.serviceEndpoint, agent.agentConfig.endpoints[0])
    }
}
