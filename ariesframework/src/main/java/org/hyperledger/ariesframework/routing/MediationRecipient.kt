package org.hyperledger.ariesframework.routing

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.AgentEvents
import org.hyperledger.ariesframework.agent.Dispatcher
import org.hyperledger.ariesframework.agent.MediatorPickupStrategy
import org.hyperledger.ariesframework.agent.MessageSerializer
import org.hyperledger.ariesframework.connection.messages.TrustPingMessage
import org.hyperledger.ariesframework.connection.models.ConnectionState
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.hyperledger.ariesframework.oob.InvitationUrlParser
import org.hyperledger.ariesframework.routing.handlers.BatchHandler
import org.hyperledger.ariesframework.routing.handlers.KeylistUpdateResponseHandler
import org.hyperledger.ariesframework.routing.handlers.MediationDenyHandler
import org.hyperledger.ariesframework.routing.handlers.MediationGrantHandler
import org.hyperledger.ariesframework.routing.messages.BatchMessage
import org.hyperledger.ariesframework.routing.messages.BatchPickupMessage
import org.hyperledger.ariesframework.routing.messages.ForwardMessage
import org.hyperledger.ariesframework.routing.messages.KeylistUpdate
import org.hyperledger.ariesframework.routing.messages.KeylistUpdateAction
import org.hyperledger.ariesframework.routing.messages.KeylistUpdateMessage
import org.hyperledger.ariesframework.routing.messages.KeylistUpdateResponseMessage
import org.hyperledger.ariesframework.routing.messages.MediationDenyMessage
import org.hyperledger.ariesframework.routing.messages.MediationGrantMessage
import org.hyperledger.ariesframework.routing.messages.MediationRequestMessage
import org.hyperledger.ariesframework.routing.repository.MediationRecord
import org.hyperledger.ariesframework.routing.repository.MediationRepository
import org.hyperledger.ariesframework.routing.repository.MediationRole
import org.hyperledger.ariesframework.routing.repository.MediationState
import org.hyperledger.ariesframework.util.DIDParser
import org.slf4j.LoggerFactory
import java.util.Timer
import kotlin.concurrent.timer

class MediationRecipient(private val agent: Agent, private val dispatcher: Dispatcher) {
    private val logger = LoggerFactory.getLogger(MediationRecipient::class.java)
    val repository = MediationRepository(agent)
    var keylistUpdateDone = false
    private var pickupTimer: Timer? = null

    init {
        registerHandlers(dispatcher)
        registerMessages()
    }

    fun close() {
        pickupTimer?.cancel()
    }

    private fun registerHandlers(dispatcher: Dispatcher) {
        dispatcher.registerHandler(MediationDenyHandler(agent))
        dispatcher.registerHandler(MediationGrantHandler(agent))
        dispatcher.registerHandler(BatchHandler(agent))
        dispatcher.registerHandler(KeylistUpdateResponseHandler(agent))
    }

    private fun registerMessages() {
        MessageSerializer.registerMessage(BatchMessage.type, BatchMessage::class)
        MessageSerializer.registerMessage(BatchPickupMessage.type, BatchPickupMessage::class)
        MessageSerializer.registerMessage(KeylistUpdateMessage.type, KeylistUpdateMessage::class)
        MessageSerializer.registerMessage(KeylistUpdateResponseMessage.type, KeylistUpdateResponseMessage::class)
        MessageSerializer.registerMessage(MediationDenyMessage.type, MediationDenyMessage::class)
        MessageSerializer.registerMessage(MediationGrantMessage.type, MediationGrantMessage::class)
        MessageSerializer.registerMessage(MediationRequestMessage.type, MediationRequestMessage::class)
        MessageSerializer.registerMessage(ForwardMessage.type, ForwardMessage::class)
    }

    suspend fun getRoutingInfo(): Pair<List<String>, List<String>> {
        val mediator = repository.getDefault()
        val endpoints = if (mediator?.endpoint == null) {
            agent.agentConfig.endpoints
        } else {
            listOf(mediator.endpoint!!)
        }
        val routingKeys = mediator?.routingKeys ?: emptyList()

        return Pair(endpoints, routingKeys)
    }

    suspend fun getRouting(): Routing {
        val (endpoints, routingKeys) = getRoutingInfo()
        val (did, verkey) = agent.wallet.createDid()
        val mediator = repository.getDefault()
        if (agent.agentConfig.mediatorConnectionsInvite != null && mediator != null && mediator.isReady()) {
            keylistUpdate(mediator, verkey)
        }

        return Routing(endpoints, verkey, did, routingKeys, mediator?.id)
    }

    suspend fun initialize(mediatorConnectionsInvite: String) {
        logger.debug("Initialize mediation with invitation: $mediatorConnectionsInvite")

        val (outOfBandInvitation, invitation) = InvitationUrlParser.parseUrl(mediatorConnectionsInvite)
        val recipientKey = outOfBandInvitation?.invitationKey() ?: invitation?.recipientKeys?.first()
            ?: throw RuntimeException("Invalid mediation invitation. Invitation must have at least one recipient key.")

        assertInvitationUrl()
        val connection = agent.connectionService.findByInvitationKey(recipientKey)
        if (connection != null && connection.isReady()) {
            requestMediationIfNecessry(connection)
        } else {
            val connection = agent.connectionService.processInvitation(invitation, outOfBandInvitation, getRouting(), true)
            val message = agent.connectionService.createRequest(connection.id)
            agent.messageSender.send(message)

            if (agent.connectionService.fetchState(connection) != ConnectionState.Complete) {
                val result = agent.eventBus.waitFor<AgentEvents.ConnectionEvent> { it.record.state == ConnectionState.Complete }
                if (!result) {
                    throw RuntimeException("Connection to the mediator timed out.")
                }
            }

            // Update connection record after the connection protocol.
            val connectionRecord = agent.connectionRepository.getById(connection.id)
            requestMediationIfNecessry(connectionRecord)
        }
    }

    // TODO: support multiple mediators
    private suspend fun assertInvitationUrl() {
        val mediationRecord = repository.getDefault()
        if (mediationRecord != null && !hasSameInvitationUrl(mediationRecord)) {
            repository.delete(mediationRecord)
        }
    }

    private suspend fun requestMediationIfNecessry(connection: ConnectionRecord) {
        var mediationRecord = repository.getDefault()
        if (mediationRecord != null) {
            if (mediationRecord.isReady()) {
                initiateMessagePickup(mediationRecord)
                agent.setInitialized()
                return
            }

            repository.delete(mediationRecord)
        }

        // If mediation request has not been done yet, start it.
        val message = createRequest(connection)
        agent.messageSender.send(message)

        mediationRecord = repository.getByConnectionId(connection.id)
        if (mediationRecord.state == MediationState.Requested) {
            val result = agent.eventBus.waitFor<AgentEvents.MediationEvent> { it.record.state != MediationState.Requested }
            if (!result) {
                throw RuntimeException("Mediation request timed out.")
            }
        }
        mediationRecord = repository.getByConnectionId(connection.id)
        if (mediationRecord.state == MediationState.Denied) {
            throw RuntimeException("Mediation request denied.")
        }
        mediationRecord.assertReady()
    }

    private fun hasSameInvitationUrl(mediationRecord: MediationRecord): Boolean {
        return mediationRecord.invitationUrl == agent.agentConfig.mediatorConnectionsInvite
    }

    @OptIn(DelicateCoroutinesApi::class)
    private suspend fun initiateMessagePickup(mediator: MediationRecord) {
        val mediatorConnection = agent.connectionRepository.getById(mediator.connectionId)
        pickupTimer = timer(period = agent.agentConfig.mediatorPollingInterval * 1000) {
            GlobalScope.launch {
                pickupMessages(mediatorConnection)
            }
        }
    }

    suspend fun pickupMessages(mediatorConnection: ConnectionRecord) {
        mediatorConnection.assertReady()

        if (agent.agentConfig.mediatorPickupStrategy == MediatorPickupStrategy.PickUpV1) {
            val message = OutboundMessage(BatchPickupMessage(10), mediatorConnection)
            try {
                agent.messageSender.send(message)
            } catch (e: Exception) {
                logger.debug("Pickup messages failed with the following error: ${e.message}")
            }
        } else if (agent.agentConfig.mediatorPickupStrategy == MediatorPickupStrategy.Implicit) {
            // For implicit pickup, responseRequested must be set to false.
            // Since no response is requested, the mediator can respond with queued messages.
            // Otherwise, it would respond with a trust ping response.
            val message = OutboundMessage(TrustPingMessage("pickup", false), mediatorConnection)
            try {
                agent.messageSender.send(message, "ws")
            } catch (e: Exception) {
                logger.debug("Pickup messages failed with the following error: ${e.message}")
            }
        } else {
            throw RuntimeException("Unsupported mediator pickup strategy: ${agent.agentConfig.mediatorPickupStrategy}")
        }
    }

    suspend fun pickupMessages() {
        val mediator = repository.getDefault()
        if (mediator == null || !mediator.isReady()) {
            return
        }
        val mediatorConnection = agent.connectionRepository.getById(mediator.connectionId)
        pickupMessages(mediatorConnection)
    }

    suspend fun createRequest(connection: ConnectionRecord): OutboundMessage {
        val message = MediationRequestMessage(Clock.System.now())
        val mediationRecord = MediationRecord(
            state = MediationState.Requested,
            role = MediationRole.Mediator,
            connectionId = connection.id,
            threadId = connection.id,
            invitationUrl = agent.agentConfig.mediatorConnectionsInvite!!,
        )
        repository.save(mediationRecord)
        agent.eventBus.publish(AgentEvents.MediationEvent(mediationRecord.copy()))

        return OutboundMessage(message, connection)
    }

    suspend fun processMediationGrant(messageContext: InboundMessageContext) {
        val connection = messageContext.assertReadyConnection()
        val mediationRecord = repository.getByConnectionId(connection.id)
        val message = MessageSerializer.decodeFromString(messageContext.plaintextMessage) as MediationGrantMessage

        mediationRecord.assertState(MediationState.Requested)

        mediationRecord.endpoint = message.endpoint
        mediationRecord.routingKeys = message.routingKeys.map { key ->
            if (key.startsWith("did:key:")) {
                DIDParser.convertDidKeyToVerkey(key)
            } else {
                key
            }
        }
        mediationRecord.state = MediationState.Granted
        repository.update(mediationRecord)
        agent.eventBus.publish(AgentEvents.MediationEvent(mediationRecord.copy()))
        agent.setInitialized()
        initiateMessagePickup(mediationRecord)
    }

    suspend fun processMediationDeny(messageContext: InboundMessageContext) {
        val connection = messageContext.assertReadyConnection()
        val mediationRecord = repository.getByConnectionId(connection.id)
        mediationRecord.assertState(MediationState.Requested)

        mediationRecord.state = MediationState.Denied
        repository.update(mediationRecord)
        agent.eventBus.publish(AgentEvents.MediationEvent(mediationRecord.copy()))
    }

    suspend fun processBatchMessage(messageContext: InboundMessageContext) {
        if (messageContext.connection == null) {
            throw Exception("No connection associated with incoming message with id ${messageContext.message.id}")
        }
        val message = MessageSerializer.decodeFromString(messageContext.plaintextMessage) as BatchMessage

        logger.debug("Get ${message.messages.size} batch messages")
        val forwardedMessages = message.messages
        for (forwardedMessage in forwardedMessages) {
            agent.receiveMessage(forwardedMessage.message)
        }
    }

    suspend fun processKeylistUpdateResults(messageContext: InboundMessageContext) {
        val connection = messageContext.assertReadyConnection()
        val mediationRecord = repository.getByConnectionId(connection.id)
        mediationRecord.assertReady()

        val message = MessageSerializer.decodeFromString(messageContext.plaintextMessage) as KeylistUpdateResponseMessage
        for (update in message.updated) {
            if (update.action == KeylistUpdateAction.ADD) {
                logger.info("Key ${update.recipientKey} added to keylist")
            } else if (update.action == KeylistUpdateAction.REMOVE) {
                logger.info("Key ${update.recipientKey} removed from keylist")
            }
        }
        keylistUpdateDone = true
        agent.eventBus.publish(message)
    }

    suspend fun keylistUpdate(mediator: MediationRecord, verkey: String) {
        mediator.assertReady()
        val keylistUpdateMessage = KeylistUpdateMessage(listOf(KeylistUpdate(verkey, KeylistUpdateAction.ADD)))
        val connection = agent.connectionRepository.getById(mediator.connectionId)
        val message = OutboundMessage(keylistUpdateMessage, connection)

        keylistUpdateDone = false
        agent.messageSender.send(message)

        if (!keylistUpdateDone) {
            val result = agent.eventBus.waitFor<KeylistUpdateResponseMessage> { it.updated.isNotEmpty() }
            if (!result) {
                throw Exception("Keylist update timed out")
            }
        }
    }
}
