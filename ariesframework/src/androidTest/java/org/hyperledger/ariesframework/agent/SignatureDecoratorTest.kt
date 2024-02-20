package org.hyperledger.ariesframework.agent

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.hyperledger.ariesframework.TestHelper
import org.hyperledger.ariesframework.agent.decorators.SignatureDecorator
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SignatureDecoratorTest {
    lateinit var agent: Agent

    val data = """
    {"did":"did","did_doc":{"@context":"https://w3id.org/did/v1","service":[{"id":"did:example:123456789abcdefghi#did-communication","type":"did-communication","priority":0,"recipientKeys":["someVerkey"],"routingKeys":[],"serviceEndpoint":"https://agent.example.com/"}]}}
    """.trimIndent()

    val signedData = SignatureDecorator(
        "https://didcomm.org/signature/1.0/ed25519Sha512_single",
        "AAAAAAAAAAB7ImRpZCI6ImRpZCIsImRpZF9kb2MiOnsiQGNvbnRleHQiOiJodHRwczovL3czaWQub3JnL2RpZC92MSIsInNlcnZpY2UiOlt7ImlkIjoiZGlkOmV4YW1wbGU6MTIzNDU2Nzg5YWJjZGVmZ2hpI2RpZC1jb21tdW5pY2F0aW9uIiwidHlwZSI6ImRpZC1jb21tdW5pY2F0aW9uIiwicHJpb3JpdHkiOjAsInJlY2lwaWVudEtleXMiOlsic29tZVZlcmtleSJdLCJyb3V0aW5nS2V5cyI6W10sInNlcnZpY2VFbmRwb2ludCI6Imh0dHBzOi8vYWdlbnQuZXhhbXBsZS5jb20vIn1dfX0", // ktlint-disable max-line-length
        "GjZWsBLgZCR18aL468JAT7w9CZRiBnpxUPPgyQxh4voa",
        "zOSmKNCHKqOJGDJ6OlfUXTPJiirEAXrFn1kPiFDZfvG5hNTBKhsSzqAvlg44apgWBu7O57vGWZsXBF2BWZ5JAw",
    )

    @Before
    fun setUp() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = TestHelper.getBaseConfig()
        agent = Agent(context, config)
        agent.wallet.initialize()
    }

    @After
    fun tearDown() = runTest {
        agent.wallet.delete()
    }

    @Test
    fun testSignData() = runTest {
        val seed1 = "00000000000000000000000000000My1"
        val (_, verkey) = agent.wallet.createDid(seed1)

        val signedData1 = SignatureDecorator.signData(data.toByteArray(), agent.wallet, verkey)
        assertEquals(signedData1.signatureType, signedData.signatureType)
        assertEquals(signedData1.signatureData, signedData.signatureData)
        assertEquals(signedData1.signer, signedData.signer)
        assertEquals(signedData1.signature, signedData.signature)
    }

    @Test
    fun testUnpack() = runTest {
        val unpackedData = signedData.unpackData()
        val unpacked = String(unpackedData)
        assertEquals(unpacked, data)
    }
}
