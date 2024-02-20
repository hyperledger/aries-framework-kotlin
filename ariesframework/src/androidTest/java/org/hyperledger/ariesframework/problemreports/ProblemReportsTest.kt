package org.hyperledger.ariesframework.problemreports

import androidx.test.filters.LargeTest
import kotlinx.coroutines.test.runTest
import org.hyperledger.ariesframework.TestHelper
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.AgentEvents
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.hyperledger.ariesframework.credentials.models.AcceptOfferOptions
import org.hyperledger.ariesframework.credentials.models.AutoAcceptCredential
import org.hyperledger.ariesframework.credentials.models.CreateOfferOptions
import org.hyperledger.ariesframework.credentials.models.CredentialPreview
import org.hyperledger.ariesframework.credentials.models.CredentialState
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.problemreports.messages.CredentialProblemReportMessage
import org.hyperledger.ariesframework.problemreports.messages.PresentationProblemReportMessage
import org.hyperledger.ariesframework.proofs.ProofService
import org.hyperledger.ariesframework.proofs.models.AttributeFilter
import org.hyperledger.ariesframework.proofs.models.PredicateType
import org.hyperledger.ariesframework.proofs.models.ProofAttributeInfo
import org.hyperledger.ariesframework.proofs.models.ProofPredicateInfo
import org.hyperledger.ariesframework.proofs.models.ProofRequest
import org.hyperledger.ariesframework.proofs.models.ProofState
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.logging.Logger
import kotlin.time.Duration.Companion.seconds

class ProblemReportsTest {
    lateinit var faberAgent: Agent
    lateinit var aliceAgent: Agent
    lateinit var credDefId: String
    lateinit var faberConnection: ConnectionRecord
    lateinit var aliceConnection: ConnectionRecord

    val logger = Logger.getLogger("ProblemReportsTest")
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

    @Test @LargeTest
    fun testCredentialDeclinedProblemReport() = runTest {
        // Faber starts with credential offer to Alice.
        var faberCredentialRecord = faberAgent.credentials.offerCredential(
            CreateOfferOptions(faberConnection, credDefId, credentialPreview.attributes, null, "Offer to Alice"),
        )

        val threadId = faberCredentialRecord.threadId
        var aliceCredentialRecord = getCredentialRecord(aliceAgent, threadId)
        assertEquals(aliceCredentialRecord.state, CredentialState.OfferReceived)

        faberAgent.eventBus.subscribe<AgentEvents.ProblemReportEvent> {
            logger.info("Problem report received: $it")
            assertEquals(it.message.threadId, threadId)
            assertTrue(it.message is CredentialProblemReportMessage)
        }

        // Alice declines the offer - so faber agent should receive a problem report.
        aliceAgent.credentials.declineOffer(AcceptOfferOptions(aliceCredentialRecord.id))
    }

    @Test @LargeTest
    fun testProofDeclinedProblemReport() = runTest {
        issueCredential()
        val proofRequest = getProofRequest()
        var faberProofRecord = faberAgent.proofs.requestProof(faberConnection.id, proofRequest)

        val threadId = faberProofRecord.threadId
        var aliceProofRecord = getProofRecord(aliceAgent, threadId)
        assertEquals(ProofState.RequestReceived, aliceProofRecord.state)

        faberAgent.eventBus.subscribe<AgentEvents.ProblemReportEvent> {
            logger.info("Problem report received: $it")
            assertEquals(it.message.threadId, threadId)
            assertTrue(it.message is PresentationProblemReportMessage)
        }

        aliceAgent.proofs.declineRequest(aliceProofRecord.id)
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
}
