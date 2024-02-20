package org.hyperledger.ariesframework.credentials

import androidx.test.filters.LargeTest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.hyperledger.ariesframework.TestHelper
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.hyperledger.ariesframework.credentials.messages.IssueCredentialMessage
import org.hyperledger.ariesframework.credentials.models.AcceptCredentialOptions
import org.hyperledger.ariesframework.credentials.models.AcceptOfferOptions
import org.hyperledger.ariesframework.credentials.models.AcceptRequestOptions
import org.hyperledger.ariesframework.credentials.models.AutoAcceptCredential
import org.hyperledger.ariesframework.credentials.models.CreateOfferOptions
import org.hyperledger.ariesframework.credentials.models.CredentialPreview
import org.hyperledger.ariesframework.credentials.models.CredentialState
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class CredentialsTest {
    lateinit var faberAgent: Agent
    lateinit var aliceAgent: Agent
    lateinit var credDefId: String
    lateinit var faberConnection: ConnectionRecord
    lateinit var aliceConnection: ConnectionRecord

    val credentialPreview = CredentialPreview.fromDictionary(mapOf("name" to "John", "age" to "99"))

    @Before
    fun setUp() = runTest(timeout = 30.seconds) {
        val (agents, connections) = TestHelper.setupCredentialTests()
        faberAgent = agents.first
        aliceAgent = agents.second
        faberConnection = connections.first
        aliceConnection = connections.second
        credDefId = TestHelper.prepareForIssuance(faberAgent, listOf("name", "age"))
    }

    @After
    fun tearDown() = runTest {
        faberAgent.reset()
        aliceAgent.reset()
    }

    suspend fun getCredentialRecord(agent: Agent, threadId: String): CredentialExchangeRecord {
        return agent.credentialExchangeRepository.getByThreadAndConnectionId(threadId, null)
    }

    @Test @LargeTest
    fun testCredentialOffer() = runTest {
        // Faber starts with credential offer to Alice.
        var faberCredentialRecord = faberAgent.credentials.offerCredential(
            CreateOfferOptions(faberConnection, credDefId, credentialPreview.attributes, null, "Offer to Alice"),
        )

        val threadId = faberCredentialRecord.threadId
        var aliceCredentialRecord = getCredentialRecord(aliceAgent, threadId)
        assertEquals(aliceCredentialRecord.state, CredentialState.OfferReceived)

        aliceAgent.credentials.acceptOffer(AcceptOfferOptions(aliceCredentialRecord.id))
        faberCredentialRecord = getCredentialRecord(faberAgent, threadId)
        assertEquals(faberCredentialRecord.state, CredentialState.RequestReceived)

        faberAgent.credentials.acceptRequest(AcceptRequestOptions(faberCredentialRecord.id))
        aliceCredentialRecord = getCredentialRecord(aliceAgent, threadId)
        assertEquals(aliceCredentialRecord.state, CredentialState.CredentialReceived)

        aliceAgent.credentials.acceptCredential(AcceptCredentialOptions(aliceCredentialRecord.id))
        aliceCredentialRecord = getCredentialRecord(aliceAgent, threadId)
        assertEquals(aliceCredentialRecord.state, CredentialState.Done)
        faberCredentialRecord = getCredentialRecord(faberAgent, threadId)
        assertEquals(faberCredentialRecord.state, CredentialState.Done)

        val credentialMessage = aliceAgent.credentials.findCredentialMessage(aliceCredentialRecord.id)
        assertNotNull(credentialMessage)
        val attachment = credentialMessage?.getCredentialAttachmentById(IssueCredentialMessage.INDY_CREDENTIAL_ATTACHMENT_ID)
        assertNotNull(attachment)

        val credentialJson = attachment?.getDataAsString()
        val credential = Json.decodeFromString<JsonObject>(credentialJson!!)
        val values = credential["values"]!!.jsonObject
        val age = values["age"]!!.jsonObject
        assertEquals(age["raw"]!!.jsonPrimitive.content, "99")
        assertEquals(age["encoded"]!!.jsonPrimitive.content, "99")

        val name = values["name"]!!.jsonObject
        assertEquals(name["raw"]!!.jsonPrimitive.content, "John")
        assertEquals(
            name["encoded"]!!.jsonPrimitive.content,
            "76355713903561865866741292988746191972523015098789458240077478826513114743258",
        )
    }

    @Test @LargeTest
    fun testAutoAcceptAgentConfig() = runTest {
        aliceAgent.agentConfig.autoAcceptCredential = AutoAcceptCredential.Always
        faberAgent.agentConfig.autoAcceptCredential = AutoAcceptCredential.Always

        var faberCredentialRecord = faberAgent.credentials.offerCredential(
            CreateOfferOptions(faberConnection, credDefId, credentialPreview.attributes, null, "Offer to Alice"),
        )

        val threadId = faberCredentialRecord.threadId
        var aliceCredentialRecord = getCredentialRecord(aliceAgent, threadId)
        faberCredentialRecord = getCredentialRecord(faberAgent, threadId)

        assertEquals(aliceCredentialRecord.state, CredentialState.Done)
        assertEquals(faberCredentialRecord.state, CredentialState.Done)
    }

    @Test @LargeTest
    fun testAutoAcceptOptions() = runTest {
        // Only faberAgent auto accepts.
        var faberCredentialRecord = faberAgent.credentials.offerCredential(
            CreateOfferOptions(
                faberConnection,
                credDefId,
                credentialPreview.attributes,
                AutoAcceptCredential.Always,
                "Offer to Alice",
            ),
        )

        val threadId = faberCredentialRecord.threadId
        var aliceCredentialRecord = getCredentialRecord(aliceAgent, threadId)
        faberCredentialRecord = getCredentialRecord(faberAgent, threadId)

        assertEquals(aliceCredentialRecord.state, CredentialState.OfferReceived)
        assertEquals(faberCredentialRecord.state, CredentialState.OfferSent)

        // aliceAgent auto accepts too.
        aliceAgent.credentials.acceptOffer(AcceptOfferOptions(aliceCredentialRecord.id, autoAcceptCredential = AutoAcceptCredential.Always))
        aliceCredentialRecord = getCredentialRecord(aliceAgent, threadId)
        faberCredentialRecord = getCredentialRecord(faberAgent, threadId)
        assertEquals(aliceCredentialRecord.state, CredentialState.Done)
        assertEquals(faberCredentialRecord.state, CredentialState.Done)
    }
}
