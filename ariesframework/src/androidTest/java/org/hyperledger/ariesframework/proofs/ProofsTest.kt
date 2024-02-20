package org.hyperledger.ariesframework.proofs

import androidx.test.filters.LargeTest
import kotlinx.coroutines.test.runTest
import org.hyperledger.ariesframework.TestHelper
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.hyperledger.ariesframework.credentials.models.AutoAcceptCredential
import org.hyperledger.ariesframework.credentials.models.CreateOfferOptions
import org.hyperledger.ariesframework.credentials.models.CredentialPreview
import org.hyperledger.ariesframework.credentials.models.CredentialState
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.proofs.models.AttributeFilter
import org.hyperledger.ariesframework.proofs.models.AutoAcceptProof
import org.hyperledger.ariesframework.proofs.models.PredicateType
import org.hyperledger.ariesframework.proofs.models.ProofAttributeInfo
import org.hyperledger.ariesframework.proofs.models.ProofPredicateInfo
import org.hyperledger.ariesframework.proofs.models.ProofRequest
import org.hyperledger.ariesframework.proofs.models.ProofState
import org.hyperledger.ariesframework.proofs.models.RevocationInterval
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class ProofsTest {
    lateinit var faberAgent: Agent
    lateinit var aliceAgent: Agent
    lateinit var credDefId: String
    lateinit var faberConnection: ConnectionRecord
    lateinit var aliceConnection: ConnectionRecord

    val credentialPreview = CredentialPreview.fromDictionary(mapOf("name" to "John", "age" to "99", "sex" to "Male"))

    @Before
    fun setUp() = runTest(timeout = 30.seconds) {
        val (agents, connections) = TestHelper.setupCredentialTests()
        faberAgent = agents.first
        aliceAgent = agents.second
        faberConnection = connections.first
        aliceConnection = connections.second
        credDefId = TestHelper.prepareForIssuance(faberAgent, listOf("name", "age", "sex"))
    }

    @After
    fun tearDown() = runTest {
        faberAgent.reset()
        aliceAgent.reset()
    }

    suspend fun getCredentialRecord(agent: Agent, threadId: String): CredentialExchangeRecord {
        return agent.credentialExchangeRepository.getByThreadAndConnectionId(threadId, null)
    }

    suspend fun getProofRecord(agent: Agent, threadId: String): ProofExchangeRecord {
        return agent.proofRepository.getByThreadAndConnectionId(threadId, null)
    }

    suspend fun issueCredential() {
        aliceAgent.agentConfig.autoAcceptCredential = AutoAcceptCredential.Always
        faberAgent.agentConfig.autoAcceptCredential = AutoAcceptCredential.Always

        var faberCredentialRecord = faberAgent.credentials.offerCredential(
            CreateOfferOptions(
                connection = faberConnection,
                credentialDefinitionId = credDefId,
                attributes = credentialPreview.attributes,
                comment = "Offer to Alice",
            ),
        )

        val threadId = faberCredentialRecord.threadId
        val aliceCredentialRecord = getCredentialRecord(aliceAgent, threadId)
        faberCredentialRecord = getCredentialRecord(faberAgent, threadId)

        assertEquals(CredentialState.Done, aliceCredentialRecord.state)
        assertEquals(CredentialState.Done, faberCredentialRecord.state)
    }

    suspend fun getProofRequest(): ProofRequest {
        val attributes = mapOf(
            "name" to ProofAttributeInfo("name", null, null, listOf(AttributeFilter(credentialDefinitionId = credDefId))),
        )
        val predicates = mapOf(
            "age" to ProofPredicateInfo(
                "age",
                null,
                PredicateType.GreaterThanOrEqualTo,
                50,
                listOf(AttributeFilter(credentialDefinitionId = credDefId)),
            ),
        )
        val nonce = ProofService.generateProofRequestNonce()
        return ProofRequest(nonce = nonce, requestedAttributes = attributes, requestedPredicates = predicates)
    }

    suspend fun getFailingProofRequest(): ProofRequest {
        val attributes = mapOf(
            "name" to ProofAttributeInfo("name", null, null, listOf(AttributeFilter(credentialDefinitionId = credDefId))),
        )
        val predicates = mapOf(
            "age" to ProofPredicateInfo(
                "age",
                null,
                PredicateType.LessThan,
                50,
                listOf(AttributeFilter(credentialDefinitionId = credDefId)),
            ),
        )

        val nonce = ProofService.generateProofRequestNonce()
        return ProofRequest(nonce = nonce, requestedAttributes = attributes, requestedPredicates = predicates)
    }

    @Test @LargeTest
    fun testProofRequest() = runTest {
        issueCredential()
        val proofRequest = getProofRequest()
        var faberProofRecord = faberAgent.proofs.requestProof(faberConnection.id, proofRequest)

        val threadId = faberProofRecord.threadId
        var aliceProofRecord = getProofRecord(aliceAgent, threadId)
        assertEquals(ProofState.RequestReceived, aliceProofRecord.state)

        val retrievedCredentials = aliceAgent.proofs.getRequestedCredentialsForProofRequest(aliceProofRecord.id)
        val requestedCredentials = aliceAgent.proofService.autoSelectCredentialsForProofRequest(retrievedCredentials)
        aliceAgent.proofs.acceptRequest(aliceProofRecord.id, requestedCredentials)

        faberProofRecord = getProofRecord(faberAgent, threadId)
        assertEquals(ProofState.PresentationReceived, faberProofRecord.state)
        assertEquals(true, faberProofRecord.isVerified)

        faberProofRecord = faberAgent.proofs.acceptPresentation(faberProofRecord.id)

        aliceProofRecord = getProofRecord(aliceAgent, threadId)
        assertEquals(ProofState.Done, aliceProofRecord.state)
        assertEquals(ProofState.Done, faberProofRecord.state)
    }

    @Test @LargeTest
    fun testAutoAcceptAgentConfig() = runTest {
        aliceAgent.agentConfig.autoAcceptProof = AutoAcceptProof.Always
        faberAgent.agentConfig.autoAcceptProof = AutoAcceptProof.Always

        issueCredential()
        val proofRequest = getProofRequest()
        var faberProofRecord = faberAgent.proofs.requestProof(faberConnection.id, proofRequest)

        val threadId = faberProofRecord.threadId
        val aliceProofRecord = getProofRecord(aliceAgent, threadId)
        faberProofRecord = getProofRecord(faberAgent, threadId)

        assertEquals(ProofState.Done, aliceProofRecord.state)
        assertEquals(ProofState.Done, faberProofRecord.state)
        assertEquals(true, faberProofRecord.isVerified)
    }

    @Test @LargeTest
    fun testProofWithoutCredential() = runTest {
        // issueCredential() is omitted.

        val proofRequest = getProofRequest()
        val faberProofRecord = faberAgent.proofs.requestProof(faberConnection.id, proofRequest)

        val threadId = faberProofRecord.threadId
        val aliceProofRecord = getProofRecord(aliceAgent, threadId)
        assertEquals(ProofState.RequestReceived, aliceProofRecord.state)

        val retrievedCredentials = aliceAgent.proofs.getRequestedCredentialsForProofRequest(aliceProofRecord.id)
        assertEquals(0, retrievedCredentials.requestedAttributes["name"]!!.size)
        assertEquals(0, retrievedCredentials.requestedPredicates["age"]!!.size)

        try {
            aliceAgent.proofService.autoSelectCredentialsForProofRequest(retrievedCredentials)
            throw Exception("Exception must be thrown")
        } catch (e: Exception) {
            // Expected
        }
    }

    @Test @LargeTest
    fun testProofWithFailingPredicates() = runTest {
        issueCredential()
        val proofRequest = getFailingProofRequest()
        val faberProofRecord = faberAgent.proofs.requestProof(faberConnection.id, proofRequest)

        val threadId = faberProofRecord.threadId
        val aliceProofRecord = getProofRecord(aliceAgent, threadId)
        assertEquals(ProofState.RequestReceived, aliceProofRecord.state)

        val retrievedCredentials = aliceAgent.proofs.getRequestedCredentialsForProofRequest(aliceProofRecord.id)
        assertEquals(1, retrievedCredentials.requestedAttributes["name"]!!.size)
        // We do not filter out credentials that do not satisfy predicates.
        // assertEquals(0, retrievedCredentials.requestedPredicates["age"]!!.size)

        val requestedCredentials = aliceAgent.proofService.autoSelectCredentialsForProofRequest(retrievedCredentials)
        try {
            // This should throw an error because we cannot create a proof satisfying the predicates.
            aliceAgent.proofs.acceptRequest(aliceProofRecord.id, requestedCredentials)
            assert(false) { "Exception must be thrown" }
        } catch (e: Exception) {
            // Expected
        }
    }

    suspend fun getProofRequestWithMultipleAttributeNames(): ProofRequest {
        val attributes = mapOf(
            "attributes1" to ProofAttributeInfo(
                null,
                listOf("name", "sex"),
                null,
                listOf(AttributeFilter(credentialDefinitionId = credDefId)),
            ),
        )
        val predicates = mapOf(
            "age" to ProofPredicateInfo(
                "age",
                null,
                PredicateType.GreaterThanOrEqualTo,
                50,
                listOf(AttributeFilter(credentialDefinitionId = credDefId)),
            ),
        )
        val nonce = ProofService.generateProofRequestNonce()
        return ProofRequest(nonce = nonce, requestedAttributes = attributes, requestedPredicates = predicates)
    }

    @Test @LargeTest
    fun testProofRequestWithMultipleAttributeNames() = runTest {
        issueCredential()
        val proofRequest = getProofRequestWithMultipleAttributeNames()
        var faberProofRecord = faberAgent.proofs.requestProof(faberConnection.id, proofRequest)

        val threadId = faberProofRecord.threadId
        var aliceProofRecord = getProofRecord(aliceAgent, threadId)
        assertEquals(ProofState.RequestReceived, aliceProofRecord.state)

        val retrievedCredentials = aliceAgent.proofs.getRequestedCredentialsForProofRequest(aliceProofRecord.id)
        val requestedCredentials = aliceAgent.proofService.autoSelectCredentialsForProofRequest(retrievedCredentials)
        aliceProofRecord = aliceAgent.proofs.acceptRequest(aliceProofRecord.id, requestedCredentials)

        faberProofRecord = getProofRecord(faberAgent, threadId)
        assertEquals(ProofState.PresentationReceived, faberProofRecord.state)
        assertEquals(true, faberProofRecord.isVerified)

        faberProofRecord = faberAgent.proofs.acceptPresentation(faberProofRecord.id)

        aliceProofRecord = getProofRecord(aliceAgent, threadId)
        assertEquals(ProofState.Done, aliceProofRecord.state)
        assertEquals(ProofState.Done, faberProofRecord.state)
    }

    suspend fun getFailedProofRequestWithMultipleAttributeNames(): ProofRequest {
        val attributes = mapOf(
            "attributes1" to ProofAttributeInfo(
                null,
                listOf("name"),
                null,
                listOf(AttributeFilter(credentialDefinitionId = credDefId)),
            ),
        )
        val nonce = ProofService.generateProofRequestNonce()
        return ProofRequest(nonce = nonce, requestedAttributes = attributes, requestedPredicates = mapOf())
    }

    @Test @LargeTest
    fun testProofWithFailingPredicates2() = runTest {
        issueCredential()
        val proofRequest = getFailedProofRequestWithMultipleAttributeNames()
        val faberProofRecord = faberAgent.proofs.requestProof(faberConnection.id, proofRequest)

        val threadId = faberProofRecord.threadId
        val aliceProofRecord = getProofRecord(aliceAgent, threadId)
        assertEquals(ProofState.RequestReceived, aliceProofRecord.state)

        try {
            aliceAgent.proofs.getRequestedCredentialsForProofRequest(aliceProofRecord.id)
            throw Exception("Exception must be thrown")
        } catch (e: Exception) {
            // Expected
        }
    }

    suspend fun getProofRequestWithMultipleAttributes(): ProofRequest {
        val attributes = mapOf(
            "attributes1" to ProofAttributeInfo("name", null, null, listOf(AttributeFilter(credentialDefinitionId = credDefId))),
            "attributes2" to ProofAttributeInfo("sex", null, null, listOf(AttributeFilter(credentialDefinitionId = credDefId))),
            "attributes3" to ProofAttributeInfo("age", null, null, listOf(AttributeFilter(credentialDefinitionId = credDefId))),
        )
        val nonce = ProofService.generateProofRequestNonce()
        return ProofRequest(nonce = nonce, requestedAttributes = attributes, requestedPredicates = mapOf())
    }

    @Test @LargeTest
    fun testConcurrency() = runTest {
        issueCredential()
        val proofRequest = getProofRequestWithMultipleAttributes()

        for (i in 0..2) {
            val retrievedCredentials = aliceAgent.proofService.getRequestedCredentialsForProofRequest(proofRequest)
            val requestedCredentials = aliceAgent.proofService.autoSelectCredentialsForProofRequest(retrievedCredentials)
            aliceAgent.proofService.createProof(proofRequest.toJsonString(), requestedCredentials)
        }
    }

    suspend fun getProofRequestWithNonRevoked(): ProofRequest {
        val attributes = mapOf(
            "name" to ProofAttributeInfo("name", null, null, listOf(AttributeFilter(credentialDefinitionId = credDefId))),
        )
        val nonce = ProofService.generateProofRequestNonce()
        return ProofRequest(
            nonce = nonce,
            requestedAttributes = attributes,
            requestedPredicates = mapOf(),
            nonRevoked = RevocationInterval(null, (System.currentTimeMillis() / 1000L).toInt()),
        )
    }

    @Test @LargeTest
    fun testNonRevokedRequest() = runTest {
        aliceAgent.agentConfig.autoAcceptProof = AutoAcceptProof.Always
        faberAgent.agentConfig.autoAcceptProof = AutoAcceptProof.Always

        issueCredential()
        val proofRequest = getProofRequestWithNonRevoked()
        var faberProofRecord = faberAgent.proofs.requestProof(faberConnection.id, proofRequest)

        val threadId = faberProofRecord.threadId
        val aliceProofRecord = getProofRecord(aliceAgent, threadId)
        faberProofRecord = getProofRecord(faberAgent, threadId)

        assertEquals(ProofState.Done, aliceProofRecord.state)
        assertEquals(ProofState.Done, faberProofRecord.state)
        assertEquals(true, faberProofRecord.isVerified)
    }
}
