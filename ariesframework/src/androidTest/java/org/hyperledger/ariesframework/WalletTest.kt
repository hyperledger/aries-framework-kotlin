package org.hyperledger.ariesframework

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.MessageSerializer
import org.hyperledger.ariesframework.connection.messages.ConnectionInvitationMessage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WalletTest {
    @Before
    fun setUp() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = TestHelper.getBaseConfig()
        agent = Agent(context, config)
        agent.wallet.initialize()
    }

    @After
    fun tearDown() = runTest {
        if (agent.wallet.session != null) {
            agent.wallet.delete()
        }
    }

    @Test
    fun testInitAndDelete() = runTest {
        val wallet = agent.wallet
        assertNotNull(wallet.session)

        wallet.delete()
        assertNull(wallet.session)
    }

    @Test
    fun testPackUnpack() = runTest {
        val json = """
          {
            "@type": "${ConnectionInvitationMessage.type}",
            "@id": "04a2c382-999e-4de9-a1d2-9dec0b2fa5e4",
            "recipientKeys": ["recipientKeyOne", "recipientKeyTwo"],
            "serviceEndpoint": "https://example.com",
            "label": "test"
          }
        """

        MessageSerializer.registerMessage(ConnectionInvitationMessage.type, ConnectionInvitationMessage::class)
        val invitation = MessageSerializer.decodeFromString(json)
        val (_, verkey) = agent.wallet.createDid()
        val encryptedMessage = agent.wallet.pack(invitation, listOf(verkey), verkey)
        val decryptedMessage = agent.wallet.unpack(encryptedMessage)
        assertEquals(decryptedMessage.senderKey, verkey)
        println("decryptedMessage: ${decryptedMessage.plaintextMessage}")
        val decryptedInvitation = MessageSerializer.decodeFromString(decryptedMessage.plaintextMessage)
        assertEquals(decryptedInvitation.id, invitation.id)
        assert(decryptedInvitation is ConnectionInvitationMessage)
    }

    companion object {
        lateinit var agent: Agent
    }
}
