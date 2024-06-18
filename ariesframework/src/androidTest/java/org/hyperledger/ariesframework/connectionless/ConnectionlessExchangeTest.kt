package org.hyperledger.ariesframework.connectionless

import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.hyperledger.ariesframework.TestHelper
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.SubjectOutboundTransport
import org.hyperledger.ariesframework.connection.models.ConnectionState
import org.hyperledger.ariesframework.credentials.models.AutoAcceptCredential
import org.hyperledger.ariesframework.credentials.models.CreateOfferOptions
import org.hyperledger.ariesframework.credentials.models.CredentialPreview
import org.hyperledger.ariesframework.credentials.models.CredentialState
import org.hyperledger.ariesframework.oob.models.CreateOutOfBandInvitationConfig
import org.hyperledger.ariesframework.proofs.ProofService
import org.hyperledger.ariesframework.proofs.models.AttributeFilter
import org.hyperledger.ariesframework.proofs.models.AutoAcceptProof
import org.hyperledger.ariesframework.proofs.models.PredicateType
import org.hyperledger.ariesframework.proofs.models.ProofAttributeInfo
import org.hyperledger.ariesframework.proofs.models.ProofPredicateInfo
import org.hyperledger.ariesframework.proofs.models.ProofRequest
import org.hyperledger.ariesframework.proofs.models.ProofState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class ConnectionlessExchangeTest {
    lateinit var issuerAgent: Agent
    lateinit var holderAgent: Agent
    lateinit var verifierAgent: Agent

    lateinit var credDefId: String

    val credentialPreview = CredentialPreview.fromDictionary(mapOf("name" to "John", "age" to "99"))
    val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() = runTest(timeout = 30.seconds) {
        val issuerConfig = TestHelper.getBaseConfig("issuer", true)
        issuerConfig.autoAcceptCredential = AutoAcceptCredential.Always
        issuerAgent = Agent(context, issuerConfig)

        val holderConfig = TestHelper.getBaseConfig("holder", true)
        holderConfig.autoAcceptCredential = AutoAcceptCredential.Always
        holderConfig.autoAcceptProof = AutoAcceptProof.Always
        holderAgent = Agent(context, holderConfig)

        val verifierConfig = TestHelper.getBaseConfig("verifier", true)
        verifierConfig.autoAcceptProof = AutoAcceptProof.Always
        verifierAgent = Agent(context, verifierConfig)

        issuerAgent.initialize()
        holderAgent.initialize()
        verifierAgent.initialize()

        credDefId = TestHelper.prepareForIssuance(issuerAgent, listOf("name", "age"))
    }

    @After
    fun tearDown() = runTest {
        issuerAgent.reset()
        holderAgent.reset()
        verifierAgent.reset()
    }

    @Test @LargeTest
    fun testConnectionlessExchange() = runTest {
        issuerAgent.setOutboundTransport(SubjectOutboundTransport(holderAgent))
        holderAgent.setOutboundTransport(SubjectOutboundTransport(issuerAgent))

        val offerOptions = CreateOfferOptions(
            connection = null,
            credentialDefinitionId = credDefId,
            attributes = credentialPreview.attributes,
            comment = "credential-offer for test",
        )
        val (message, record) = issuerAgent.credentialService.createOffer(offerOptions)
        validateState(issuerAgent, record.threadId, CredentialState.OfferSent)

        val oobConfig = CreateOutOfBandInvitationConfig(
            label = "issuer-to-holder-invitation",
            alias = "issuer-to-holder-invitation",
            handshake = false,
            messages = listOf(message),
            multiUseInvitation = false,
            autoAcceptConnection = true,
        )
        val oobInvitation = issuerAgent.oob.createInvitation(oobConfig)

        val (oob, connection) = holderAgent.oob.receiveInvitation(oobInvitation.outOfBandInvitation)
        assertNotNull(connection)
        assertEquals(connection?.state, ConnectionState.Complete)
        assertNotNull(oob)

        validateState(holderAgent, record.threadId, CredentialState.Done)
        validateState(issuerAgent, record.threadId, CredentialState.Done)

        // credential exchange done.

        holderAgent.setOutboundTransport(SubjectOutboundTransport(verifierAgent))
        verifierAgent.setOutboundTransport(SubjectOutboundTransport(holderAgent))

        val proofRequest = getProofRequest()
        val (proofRequestMessage, proofExchangeRecord) = verifierAgent.proofService.createRequest(
            proofRequest,
        )
        validateState(verifierAgent, proofExchangeRecord.threadId, ProofState.RequestSent)

        val oobConfigForProofExchange = CreateOutOfBandInvitationConfig(
            label = "verifier-to-holder-invitation",
            alias = "verifier-to-holder-invitation",
            handshake = false,
            messages = listOf(proofRequestMessage),
            multiUseInvitation = false,
            autoAcceptConnection = true,
        )
        val oobInvitationForProofExchange =
            verifierAgent.oob.createInvitation(oobConfigForProofExchange)

        val (oobForProofExchange, connectionForProofExchange) = holderAgent.oob.receiveInvitation(
            oobInvitationForProofExchange.outOfBandInvitation,
        )
        assertNotNull(connectionForProofExchange)
        assertEquals(connectionForProofExchange?.state, ConnectionState.Complete)
        assertNotNull(oobForProofExchange)

        validateState(holderAgent, proofExchangeRecord.threadId, ProofState.Done)
        validateState(verifierAgent, proofExchangeRecord.threadId, ProofState.Done)
    }

    private suspend fun validateState(agent: Agent, threadId: String, state: CredentialState) {
        val record = agent.credentialExchangeRepository.getByThreadAndConnectionId(threadId, null)
        assertEquals(record.state, state)
    }

    private suspend fun validateState(agent: Agent, threadId: String, state: ProofState) {
        val record = agent.proofRepository.getByThreadAndConnectionId(threadId, null)
        assertEquals(record.state, state)
    }

    private suspend fun getProofRequest(): ProofRequest {
        val attributes = mapOf(
            "name" to ProofAttributeInfo(
                name = "name",
                restrictions = listOf(AttributeFilter(credentialDefinitionId = credDefId)),
            ),
        )
        val predicates = mapOf(
            "age" to ProofPredicateInfo(
                name = "age",
                predicateType = PredicateType.GreaterThanOrEqualTo,
                predicateValue = 50,
                restrictions = listOf(AttributeFilter(credentialDefinitionId = credDefId)),
            ),
        )
        val nonce = ProofService.generateProofRequestNonce()
        return ProofRequest(nonce = nonce, requestedAttributes = attributes, requestedPredicates = predicates)
    }
}
