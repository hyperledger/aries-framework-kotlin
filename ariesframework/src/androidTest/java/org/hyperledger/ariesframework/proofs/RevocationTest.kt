package org.hyperledger.ariesframework.proofs

import androidx.test.filters.LargeTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.hyperledger.ariesframework.TestHelper
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.hyperledger.ariesframework.credentials.models.AutoAcceptCredential
import org.hyperledger.ariesframework.credentials.models.CreateOfferOptions
import org.hyperledger.ariesframework.credentials.models.CredentialPreview
import org.hyperledger.ariesframework.credentials.models.CredentialState
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.ledger.CredentialDefinitionTemplate
import org.hyperledger.ariesframework.ledger.RevocationRegistryDefinitionTemplate
import org.hyperledger.ariesframework.ledger.SchemaTemplate
import org.hyperledger.ariesframework.proofs.models.AttributeFilter
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
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

class RevocationTest {
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
        credDefId = prepareForRevocation()
    }

    @After
    fun tearDown() = runTest {
        faberAgent.reset()
        aliceAgent.reset()
    }

    suspend fun prepareForRevocation(): String {
        val agent = faberAgent
        val didInfo = agent.wallet.publicDid
        val schemaId = agent.ledgerService.registerSchema(
            didInfo!!,
            SchemaTemplate("schema-${UUID.randomUUID()}", "1.0", listOf("name", "age", "sex")),
        )
        delay(0.1.seconds)
        val (schema, seqNo) = agent.ledgerService.getSchema(schemaId)
        val credDefId = agent.ledgerService.registerCredentialDefinition(
            didInfo,
            CredentialDefinitionTemplate(schema, "default", true, seqNo),
        )
        agent.ledgerService.registerRevocationRegistryDefinition(
            didInfo,
            RevocationRegistryDefinitionTemplate(credDefId, "default", 100),
        )

        return credDefId
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

    suspend fun revokeCredential() {
        val didInfo = faberAgent.wallet.publicDid ?: throw Exception("Faber has no public DID.")
        faberAgent.ledgerService.revokeCredential(didInfo, credDefId, 1)
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
        return ProofRequest(
            nonce = nonce,
            requestedAttributes = attributes,
            requestedPredicates = predicates,
            nonRevoked = RevocationInterval(null, (System.currentTimeMillis() / 1000L).toInt()),
        )
    }

    @Test @LargeTest
    fun testProofRequestWithNonRevoked() = runTest(timeout = 20.seconds) {
        issueCredential()
        val proofRequest = getProofRequest()
        var faberProofRecord = faberAgent.proofs.requestProof(
            connectionId = faberConnection.id,
            proofRequest = proofRequest,
        )
        delay(0.1.seconds)

        val threadId = faberProofRecord.threadId
        var aliceProofRecord = getProofRecord(aliceAgent, threadId)
        assertEquals(ProofState.RequestReceived, aliceProofRecord.state)

        val retrievedCredentials = aliceAgent.proofs.getRequestedCredentialsForProofRequest(aliceProofRecord.id)
        val requestedCredentials = aliceAgent.proofService.autoSelectCredentialsForProofRequest(retrievedCredentials)
        aliceProofRecord = aliceAgent.proofs.acceptRequest(aliceProofRecord.id, requestedCredentials)
        delay(0.1.seconds)

        faberProofRecord = getProofRecord(faberAgent, threadId)
        assertEquals(ProofState.PresentationReceived, faberProofRecord.state)
        assertEquals(true, faberProofRecord.isVerified)

        faberProofRecord = faberAgent.proofs.acceptPresentation(faberProofRecord.id)
        delay(0.1.seconds)

        aliceProofRecord = getProofRecord(aliceAgent, threadId)
        assertEquals(ProofState.Done, aliceProofRecord.state)
        assertEquals(ProofState.Done, faberProofRecord.state)
    }

    @Test @LargeTest
    fun testVerifyAfterRevocation() = runBlocking {
        aliceAgent.agentConfig.ignoreRevocationCheck = true

        issueCredential()
        revokeCredential()
        delay(10.seconds) // Wait for revocation to take effect

        val proofRequest = getProofRequest()
        var faberProofRecord = faberAgent.proofs.requestProof(
            connectionId = faberConnection.id,
            proofRequest = proofRequest,
        )
        delay(0.1.seconds)

        val threadId = faberProofRecord.threadId
        val aliceProofRecord = getProofRecord(aliceAgent, threadId)
        assertEquals(ProofState.RequestReceived, aliceProofRecord.state)

        val retrievedCredentials = aliceAgent.proofs.getRequestedCredentialsForProofRequest(aliceProofRecord.id)
        val requestedCredentials = aliceAgent.proofService.autoSelectCredentialsForProofRequest(retrievedCredentials)
        aliceAgent.proofs.acceptRequest(aliceProofRecord.id, requestedCredentials)
        delay(0.1.seconds)

        faberProofRecord = getProofRecord(faberAgent, threadId)
        assertEquals(ProofState.PresentationReceived, faberProofRecord.state)
        assertEquals(false, faberProofRecord.isVerified)
    }
}
