package org.hyperledger.ariesframework

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.AgentConfig
import org.hyperledger.ariesframework.agent.SubjectOutboundTransport
import org.hyperledger.ariesframework.connection.messages.ConnectionInvitationMessage
import org.hyperledger.ariesframework.connection.models.ConnectionRole
import org.hyperledger.ariesframework.connection.models.ConnectionState
import org.hyperledger.ariesframework.connection.models.didauth.DidCommService
import org.hyperledger.ariesframework.connection.models.didauth.DidDoc
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.hyperledger.ariesframework.credentials.models.AutoAcceptCredential
import org.hyperledger.ariesframework.ledger.CredentialDefinitionTemplate
import org.hyperledger.ariesframework.ledger.SchemaTemplate
import org.hyperledger.ariesframework.oob.messages.OutOfBandInvitation
import org.hyperledger.ariesframework.oob.models.OutOfBandRole
import org.hyperledger.ariesframework.oob.models.OutOfBandState
import org.hyperledger.ariesframework.oob.repository.OutOfBandRecord
import org.hyperledger.ariesframework.proofs.models.AutoAcceptProof
import org.slf4j.LoggerFactory
import java.io.File
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object TestHelper {
    val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    val logger = LoggerFactory.getLogger(TestHelper::class.java)
    val bcovrin = "bcovrin-genesis.txn"

    fun getBaseConfig(name: String = "alice", useLedgerSerivce: Boolean = false): AgentConfig {
        val key = "HfyxAyKK8Z2xVzWbXXy2erY32B9Bnr8WFgR5HfzjAnGx"
        copyResourceFile(bcovrin)
        return AgentConfig(
            walletId = "AFSTestWallet_$name",
            walletKey = key,
            genesisPath = File(context.filesDir.absolutePath, bcovrin).absolutePath,
            poolName = name,
            mediatorConnectionsInvite = null,
            label = "Agent_$name",
            autoAcceptCredential = AutoAcceptCredential.Never,
            autoAcceptProof = AutoAcceptProof.Never,
            useLedgerService = useLedgerSerivce,
            publicDidSeed = "00000000000000000000000AFKIssuer", // this should be registered as an endorser on bcovrin
        )
    }

    fun getBcorvinConfig(name: String = "alice"): AgentConfig {
        val config = getBaseConfig(name, true)
        config.autoAcceptCredential = AutoAcceptCredential.Always
        config.autoAcceptProof = AutoAcceptProof.Always
        return config
    }

    private fun copyResourceFile(resource: String) {
        val inputStream = context.assets.open(resource)
        val file = File(context.filesDir.absolutePath, resource)
        if (!file.exists()) {
            file.outputStream().use { inputStream.copyTo(it) }
        }
    }

    fun getMockConnection(state: ConnectionState? = null): ConnectionRecord {
        return ConnectionRecord(
            state = state ?: ConnectionState.Invited,
            role = ConnectionRole.Invitee,
            didDoc = DidDoc(
                id = "test-did",
                publicKey = emptyList(),
                service = listOf(
                    DidCommService(
                        "test-did;indy",
                        "https://endpoint.com",
                        listOf("key-1"),
                    ),
                ),
                authentication = emptyList(),
            ),
            did = "test-did",
            verkey = "key-1",
            theirDidDoc = DidDoc(
                id = "their-did",
                publicKey = emptyList(),
                service = listOf(
                    DidCommService(
                        "their-did;indy",
                        "https://endpoint.com",
                        listOf("key-1"),
                    ),
                ),
                authentication = emptyList(),
            ),
            theirDid = "their-did",
            theirLabel = "their label",
            invitation = ConnectionInvitationMessage(
                label = "test",
                recipientKeys = listOf("key-1"),
                serviceEndpoint = "https:endpoint.com/msg",
            ),
            multiUseInvitation = false,
        )
    }

    fun getMockOutOfBand(
        role: OutOfBandRole,
        state: OutOfBandState,
        reusable: Boolean,
        reuseConnectionId: String? = null,
    ): OutOfBandRecord {
        val json = """
        {
            "@type": "https://didcomm.org/out-of-band/1.1/invitation",
            "@id": "69212a3a-d068-4f9d-a2dd-4741bca89af3",
            "label": "Faber College",
            "goal_code": "issue-vc",
            "goal": "To issue a Faber College Graduate credential",
            "handshake_protocols": ["https://didcomm.org/didexchange/1.0", "https://didcomm.org/connections/1.0"],
            "services": [
                {
                    "id": "#inline",
                    "type": "did-communication",
                    "recipientKeys": ["did:key:z6MkmjY8GnV5i9YTDtPETC2uUAW6ejw3nk5mXF5yci5ab7th"],
                    "routingKeys": ["did:key:z6MkmjY8GnV5i9YTDtPETC2uUAW6ejw3nk5mXF5yci5ab7th"],
                    "serviceEndpoint": "https://example.com/ssi"
                }
            ]
        }
        """

        val invitation = OutOfBandInvitation.fromJson(json)
        return OutOfBandRecord(
            outOfBandInvitation = invitation,
            role = role,
            state = state,
            reusable = reusable,
            reuseConnectionId = reuseConnectionId,
        )
    }

    fun isConnectedWith(received: ConnectionRecord, connection: ConnectionRecord): Boolean {
        try {
            received.assertReady()
            connection.assertReady()
        } catch (e: Exception) {
            return false
        }

        return (received.theirDid == connection.did && received.theirKey() == connection.verkey)
    }

    suspend fun setupCredentialTests(): Pair<Pair<Agent, Agent>, Pair<ConnectionRecord, ConnectionRecord>> {
        val faberConfig = getBaseConfig("faber", true)
        val aliceConfig = getBaseConfig("alice", true)

        val faberAgent = Agent(context, faberConfig)
        val aliceAgent = Agent(context, aliceConfig)

        faberAgent.setOutboundTransport(SubjectOutboundTransport(aliceAgent))
        aliceAgent.setOutboundTransport(SubjectOutboundTransport(faberAgent))

        faberAgent.initialize()
        aliceAgent.initialize()

        val connections = makeConnection(faberAgent, aliceAgent)
        return Pair(Pair(faberAgent, aliceAgent), connections)
    }

    suspend fun prepareForIssuance(agent: Agent, attributes: List<String>): String {
        logger.debug("Preparing for issuance")
        val didInfo = agent.wallet.publicDid ?: throw Exception("Agent has no public DID.")
        val schemaId = agent.ledgerService.registerSchema(
            didInfo,
            SchemaTemplate("schema-${UUID.randomUUID()}", "1.0", attributes),
        )
        delay(0.1.seconds)
        val (schema, seqNo) = agent.ledgerService.getSchema(schemaId)
        return agent.ledgerService.registerCredentialDefinition(
            didInfo,
            CredentialDefinitionTemplate(schema, "default", false, seqNo),
        )
    }

    suspend fun makeConnection(agentA: Agent, agentB: Agent, waitFor: Duration = 0.1.seconds): Pair<ConnectionRecord, ConnectionRecord> {
        logger.debug("Making connection")
        val message = agentA.connections.createConnection()
        val invitation = message.payload as ConnectionInvitationMessage
        var agentAConnection = message.connection
        var agentBConnection = agentB.connections.receiveInvitation(invitation)

        delay(waitFor)

        agentAConnection = agentA.connectionRepository.getById(agentAConnection.id)
        agentBConnection = agentB.connectionRepository.getById(agentBConnection.id)
        check(agentAConnection.state == ConnectionState.Complete && agentBConnection.state == ConnectionState.Complete) {
            "Connection is not complete yet."
        }

        return Pair(agentAConnection, agentBConnection)
    }
}
