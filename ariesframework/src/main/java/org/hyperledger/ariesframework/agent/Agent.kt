package org.hyperledger.ariesframework.agent

import android.content.Context
import askar_uniffi.AskarStoreManager
import org.hyperledger.ariesframework.EncryptedMessage
import org.hyperledger.ariesframework.anoncreds.AnoncredsService
import org.hyperledger.ariesframework.anoncreds.storage.CredentialDefinitionRepository
import org.hyperledger.ariesframework.anoncreds.storage.CredentialRepository
import org.hyperledger.ariesframework.anoncreds.storage.RevocationRegistryRepository
import org.hyperledger.ariesframework.basicmessage.BasicMessageCommand
import org.hyperledger.ariesframework.connection.ConnectionCommand
import org.hyperledger.ariesframework.connection.ConnectionService
import org.hyperledger.ariesframework.connection.DidExchangeService
import org.hyperledger.ariesframework.connection.JwsService
import org.hyperledger.ariesframework.connection.PeerDIDService
import org.hyperledger.ariesframework.connection.repository.ConnectionRepository
import org.hyperledger.ariesframework.credentials.CredentialService
import org.hyperledger.ariesframework.credentials.CredentialsCommand
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRepository
import org.hyperledger.ariesframework.ledger.LedgerService
import org.hyperledger.ariesframework.oob.OutOfBandCommand
import org.hyperledger.ariesframework.oob.OutOfBandService
import org.hyperledger.ariesframework.oob.repository.OutOfBandRepository
import org.hyperledger.ariesframework.problemreports.ProblemReportsCommand
import org.hyperledger.ariesframework.proofs.ProofCommand
import org.hyperledger.ariesframework.proofs.ProofService
import org.hyperledger.ariesframework.proofs.RevocationService
import org.hyperledger.ariesframework.proofs.repository.ProofRepository
import org.hyperledger.ariesframework.routing.MediationRecipient
import org.hyperledger.ariesframework.storage.DidCommMessageRepository
import org.hyperledger.ariesframework.wallet.Wallet

class Agent(val context: Context, val agentConfig: AgentConfig) {
    val wallet: Wallet = Wallet(this)
    val eventBus = EventBus()
    val dispatcher = Dispatcher(this)
    val messageReceiver = MessageReceiver(this)
    val messageSender = MessageSender(this)
    val connectionRepository = ConnectionRepository(this)
    val connectionService = ConnectionService(this)
    val didExchangeService = DidExchangeService(this)
    val peerDIDService = PeerDIDService(this)
    val jwsService = JwsService(this)
    val connections = ConnectionCommand(this, dispatcher)
    val mediationRecipient = MediationRecipient(this, dispatcher)
    val outOfBandRepository = OutOfBandRepository(this)
    val outOfBandService = OutOfBandService(this)
    val oob = OutOfBandCommand(this, dispatcher)
    val didCommMessageRepository = DidCommMessageRepository(this)
    val credentialExchangeRepository = CredentialExchangeRepository(this)
    val ledgerService = LedgerService(this)
    val credentialDefinitionRepository = CredentialDefinitionRepository(this)
    val revocationRegistryRepository = RevocationRegistryRepository(this)
    val anoncredsService = AnoncredsService(this)
    val credentialService = CredentialService(this)
    val credentials = CredentialsCommand(this, dispatcher)
    val credentialRepository = CredentialRepository(this)
    val revocationService = RevocationService(this)
    val proofRepository = ProofRepository(this)
    val proofService = ProofService(this)
    val proofs = ProofCommand(this, dispatcher)
    val basicMessages = BasicMessageCommand(this, dispatcher)
    val problemReports = ProblemReportsCommand(this, dispatcher)

    private var _isInitialized = false

    /**
     * Initialize the agent. This will create a wallet if necessary and open it.
     * It will also connect to the mediator if configured and connect to the ledger.
     */
    suspend fun initialize() {
        wallet.initialize()

        agentConfig.publicDidSeed?.let {
            wallet.initPublicDid(it)
        }

        if (agentConfig.useLedgerService) {
            ledgerService.initialize()
        }

        if (agentConfig.mediatorConnectionsInvite != null) {
            mediationRecipient.initialize(agentConfig.mediatorConnectionsInvite!!)
        } else {
            setInitialized()
        }
    }

    fun setInitialized() {
        _isInitialized = true
    }

    /**
     * Whether the agent is initialized. Agent should make new connections after it is initialized.
     */
    fun isInitialized(): Boolean {
        return _isInitialized
    }

    /**
     * Shutdown the agent. This will close the wallet, disconnect from the ledger, disconnect from the mediator and close open websockets.
     */
    suspend fun shutdown() {
        mediationRecipient.close()
        messageSender.close()
        wallet.close()
        _isInitialized = false
    }

    /**
     * Remove the wallet and ledger data. This makes the agent as if it was never initialized.
     */
    suspend fun reset() {
        if (isInitialized()) {
            shutdown()
        }
        wallet.delete()
    }

    suspend fun receiveMessage(encryptedMessage: EncryptedMessage) {
        messageReceiver.receiveMessage(encryptedMessage)
    }

    /**
     * Set the outbound transport for the agent. This will override the default http/websocket transport.
     * It is useful for testing.
     */
    fun setOutboundTransport(outboundTransport: OutboundTransport) {
        messageSender.setOutboundTransport(outboundTransport)
    }

    companion object {
        /**
         * Generate a key to encrypt the wallet.
         */
        fun generateWalletKey(): String {
            return AskarStoreManager().generateRawStoreKey(null)
        }
    }
}
