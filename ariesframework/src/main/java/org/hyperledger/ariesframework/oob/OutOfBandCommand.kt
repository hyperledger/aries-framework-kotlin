package org.hyperledger.ariesframework.oob

import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.AgentEvents
import org.hyperledger.ariesframework.agent.Dispatcher
import org.hyperledger.ariesframework.agent.MessageSerializer
import org.hyperledger.ariesframework.connection.messages.ConnectionInvitationMessage
import org.hyperledger.ariesframework.connection.models.ConnectionRole
import org.hyperledger.ariesframework.connection.models.ConnectionState
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.hyperledger.ariesframework.oob.handlers.HandshakeReuseAcceptedHandler
import org.hyperledger.ariesframework.oob.handlers.HandshakeReuseHandler
import org.hyperledger.ariesframework.oob.messages.HandshakeReuseAcceptedMessage
import org.hyperledger.ariesframework.oob.messages.HandshakeReuseMessage
import org.hyperledger.ariesframework.oob.messages.OutOfBandInvitation
import org.hyperledger.ariesframework.oob.models.CreateOutOfBandInvitationConfig
import org.hyperledger.ariesframework.oob.models.HandshakeProtocol
import org.hyperledger.ariesframework.oob.models.OutOfBandDidDocumentService
import org.hyperledger.ariesframework.oob.models.OutOfBandRole
import org.hyperledger.ariesframework.oob.models.OutOfBandState
import org.hyperledger.ariesframework.oob.models.ReceiveOutOfBandInvitationConfig
import org.hyperledger.ariesframework.oob.repository.OutOfBandRecord
import org.hyperledger.ariesframework.util.DIDParser
import org.slf4j.LoggerFactory

class OutOfBandCommand(val agent: Agent, private val dispatcher: Dispatcher) {
    private val logger = LoggerFactory.getLogger(OutOfBandCommand::class.java)
    private val didCommProfiles = listOf("didcomm/aip1", "didcomm/aip2;env=rfc19")

    init {
        registerHandlers(dispatcher)
        registerMessages()
    }

    private fun registerHandlers(dispatcher: Dispatcher) {
        dispatcher.registerHandler(HandshakeReuseHandler(agent))
        dispatcher.registerHandler(HandshakeReuseAcceptedHandler(agent))
    }

    private fun registerMessages() {
        MessageSerializer.registerMessage(OutOfBandInvitation.type, OutOfBandInvitation::class)
        MessageSerializer.registerMessage(HandshakeReuseAcceptedMessage.type, HandshakeReuseAcceptedMessage::class)
        MessageSerializer.registerMessage(HandshakeReuseMessage.type, HandshakeReuseMessage::class)
    }

    /**
     * Creates an outbound out-of-band record containing out-of-band invitation message defined in
     * Aries RFC 0434: Out-of-Band Protocol 1.1.
     *
     * It automatically adds all supported handshake protocols by agent to `handshake_protocols`. You
     * can modify this by setting `handshakeProtocols` in `config` parameter. If you want to create
     * invitation without handshake, you can set `handshake` to `false`.
     *
     * If `config` parameter contains `messages` it adds them to `requests~attach` attribute.
     *
     * Agent role: sender (inviter)
     *
     * @param config configuration of how out-of-band invitation should be created.
     * @return out-of-band record.
     */
    suspend fun createInvitation(config: CreateOutOfBandInvitationConfig): OutOfBandRecord {
        val multiUseInvitation = config.multiUseInvitation ?: false
        val handshake = config.handshake ?: true
        val autoAcceptConnection = config.autoAcceptConnection ?: agent.agentConfig.autoAcceptConnections
        val messages = config.messages ?: emptyList()
        val label = config.label ?: agent.agentConfig.label
        val imageUrl = config.imageUrl ?: agent.agentConfig.connectionImageUrl

        require(handshake || messages.isNotEmpty()) {
            "One of handshake_protocols and requests~attach MUST be included in the message."
        }
        require(messages.isEmpty() || !multiUseInvitation) {
            "Attribute 'multiUseInvitation' can not be 'true' when 'messages' is defined."
        }

        val handshakeProtocols = if (handshake) getSupportedHandshakeProtocols() else null
        val routing = config.routing ?: agent.mediationRecipient.getRouting()
        val services = routing.endpoints.mapIndexed { index, endpoint ->
            OutOfBandDidDocumentService(
                id = "#inline-$index",
                serviceEndpoint = endpoint,
                recipientKeys = DIDParser.convertVerkeysToDidKeys(listOf(routing.verkey)),
                routingKeys = DIDParser.convertVerkeysToDidKeys(routing.routingKeys),
            )
        }

        val outOfBandInvitation = OutOfBandInvitation(
            label = label,
            goalCode = config.goalCode,
            goal = config.goal,
            accept = didCommProfiles,
            handshakeProtocols = handshakeProtocols,
            services = services,
            imageUrl = imageUrl,
        )

        if (messages.isNotEmpty()) {
            messages.forEach { message ->
                outOfBandInvitation.addRequest(message)
            }
            if (!handshake) {
                val connectionRecord = agent.connectionService.createConnection(
                    role = ConnectionRole.Inviter,
                    state = ConnectionState.Complete,
                    outOfBandInvitation = outOfBandInvitation,
                    alias = null,
                    routing = routing,
                    theirLabel = null,
                    autoAcceptConnection = true,
                    multiUseInvitation = false,
                    tags = null,
                    imageUrl = null,
                    threadId = null,
                )
                agent.connectionRepository.save(connectionRecord)
            }
        }

        val outOfBandRecord = OutOfBandRecord(
            outOfBandInvitation = outOfBandInvitation,
            role = OutOfBandRole.Sender,
            state = OutOfBandState.AwaitResponse,
            reusable = multiUseInvitation,
            autoAcceptConnection = autoAcceptConnection,
        )

        agent.outOfBandRepository.save(outOfBandRecord)
        agent.eventBus.publish(AgentEvents.OutOfBandEvent(outOfBandRecord.copy()))
        logger.debug("OutOfBandInvitation created with id: ${outOfBandInvitation.id}")

        return outOfBandRecord
    }

    /**
     * Parses URL, decodes invitation and calls `receiveInvitation` with parsed invitation message.
     *
     * Agent role: receiver (invitee)
     *
     * @param url url containing a base64 encoded invitation to receive.
     * @param config configuration of how out-of-band invitation should be received.
     * @return out-of-band record and connection record if one has been created.
     */
    suspend fun receiveInvitationFromUrl(
        url: String,
        config: ReceiveOutOfBandInvitationConfig? = null,
    ): Pair<OutOfBandRecord?, ConnectionRecord?> {
        val (outOfBandInvitation, invitation) = parseInvitationShortUrl(url)
        if (invitation != null) {
            val connection = agent.connections.receiveInvitation(invitation, null, config?.autoAcceptConnection, config?.alias)
            return Pair(null, connection)
        }

        return receiveInvitation(outOfBandInvitation!!, config)
    }

    /**
     * Parses URL containing encoded invitation and returns invitation message. Compatible with
     * parsing shortened URLs.
     *
     * @param url url containing either a base64url encoded invitation or shortened URL.
     * @return out-of-band invitation and connection invitation if one has been parsed.
     */
    suspend fun parseInvitationShortUrl(url: String): Pair<OutOfBandInvitation?, ConnectionInvitationMessage?> {
        return InvitationUrlParser.parseUrl(url)
    }

    private fun getSupportedHandshakeProtocols(): List<HandshakeProtocol> {
        return listOf(HandshakeProtocol.Connections, HandshakeProtocol.DidExchange11)
    }

    /**
     * Creates inbound out-of-band record and assigns out-of-band invitation message to it if the
     * message is valid. It automatically passes out-of-band invitation for further processing to
     * `acceptInvitation` method. If you don't want to do that you can set `autoAcceptInvitation`
     * attribute in `config` parameter to `false` and accept the message later by calling
     * `acceptInvitation`.
     *
     * It supports both OOB (Aries RFC 0434: Out-of-Band Protocol 1.1) and Connection Invitation
     * (0160: Connection Protocol).
     *
     * Agent role: receiver (invitee)
     *
     * @param invitation out-of-band invitation to receive.
     * @param config configuration of how out-of-band invitation should be received.
     * @return out-of-band record and connection record if one has been created.
     */
    suspend fun receiveInvitation(
        invitation: OutOfBandInvitation,
        config: ReceiveOutOfBandInvitationConfig? = null,
    ): Pair<OutOfBandRecord, ConnectionRecord?> {
        val autoAcceptInvitation = config?.autoAcceptInvitation ?: true
        val autoAcceptConnection = config?.autoAcceptConnection ?: true
        val reuseConnection = config?.reuseConnection ?: false
        val label = config?.label ?: agent.agentConfig.label
        val alias = config?.alias
        val imageUrl = config?.imageUrl ?: agent.agentConfig.connectionImageUrl

        val messages = invitation.getRequestsJson()
        require((invitation.handshakeProtocols?.size ?: 0) > 0 || messages.isNotEmpty()) {
            "One of handshake_protocols and requests~attach MUST be included in the message."
        }
        require(invitation.fingerprints().isNotEmpty()) {
            "Invitation does not contain any valid service object."
        }

        val previousRecord = agent.outOfBandRepository.findByInvitationId(invitation.id)
        check(previousRecord == null) {
            "An out of band record with invitation ${invitation.id} already exists. Invitations should have a unique id."
        }

        val outOfBandRecord = OutOfBandRecord(
            outOfBandInvitation = invitation,
            role = OutOfBandRole.Receiver,
            state = OutOfBandState.Initial,
            reusable = false,
            autoAcceptConnection = autoAcceptConnection,
        )
        agent.outOfBandRepository.save(outOfBandRecord)
        agent.eventBus.publish(AgentEvents.OutOfBandEvent(outOfBandRecord.copy()))

        if (autoAcceptInvitation) {
            val acceptConfig = ReceiveOutOfBandInvitationConfig(
                label = label,
                alias = alias,
                imageUrl = imageUrl,
                autoAcceptConnection = autoAcceptConnection,
                reuseConnection = reuseConnection,
                routing = config?.routing,
            )
            return acceptInvitation(outOfBandId = outOfBandRecord.id, config = acceptConfig)
        }

        return Pair(outOfBandRecord, null)
    }

    /**
     * Creates a connection if the out-of-band invitation message contains `handshake_protocols`
     * attribute, except for the case when connection already exists and `reuseConnection` is enabled.
     *
     * It passes first supported message from `requests~attach` attribute to the agent, except for the
     * case reuse of connection is applied when it just sends `handshake-reuse` message to existing
     * connection.
     *
     * Agent role: receiver (invitee)
     *
     * @param outOfBandId out-of-band record id to accept.
     * @param config configuration of how out-of-band invitation should be received.
     * @return out-of-band record and connection record if one has been created.
     */
    suspend fun acceptInvitation(
        outOfBandId: String,
        config: ReceiveOutOfBandInvitationConfig? = null,
    ): Pair<OutOfBandRecord, ConnectionRecord?> {
        var outOfBandRecord = agent.outOfBandService.getById(outOfBandId)
        val existingConnection = findExistingConnection(outOfBandInvitation = outOfBandRecord.outOfBandInvitation)

        agent.outOfBandService.updateState(outOfBandRecord, OutOfBandState.PrepareResponse)

        val messages = outOfBandRecord.outOfBandInvitation.getRequestsJson()
        val handshakeProtocols = outOfBandRecord.outOfBandInvitation.handshakeProtocols ?: emptyList()
        var connectionRecord: ConnectionRecord? = null
        if (existingConnection != null && config?.reuseConnection == true) {
            if (messages.isNotEmpty()) {
                logger.debug("Skip handshake and reuse existing connection ${existingConnection.id}")
                connectionRecord = existingConnection
            } else {
                logger.debug("Start handshake to reuse connection.")
                val isHandshakeReuseSuccessful =
                    handleHandshakeReuse(outOfBandRecord = outOfBandRecord, connectionRecord = existingConnection)
                if (isHandshakeReuseSuccessful) {
                    connectionRecord = existingConnection
                } else {
                    logger.warn("Handshake reuse failed. Not using existing connection ${existingConnection.id}")
                }
            }
        }

        val handshakeProtocol = selectHandshakeProtocol(handshakeProtocols)
        if (connectionRecord == null) {
            logger.debug("Creating new connection.")
            connectionRecord = agent.connections.acceptOutOfBandInvitation(outOfBandRecord, handshakeProtocol, config)
        }

        if (handshakeProtocol != null && agent.connectionService.fetchState(connectionRecord) != ConnectionState.Complete) {
            val result = agent.eventBus.waitFor<AgentEvents.ConnectionEvent> { it.record.state == ConnectionState.Complete }
            if (!result) {
                throw Exception("Connection timed out.")
            }
        }
        connectionRecord = agent.connectionRepository.getById(connectionRecord.id)
        if (!outOfBandRecord.reusable) {
            agent.outOfBandService.updateState(outOfBandRecord, OutOfBandState.Done)
        }

        if (messages.isNotEmpty()) {
            processMessages(messages, connectionRecord)
        }
        return Pair(outOfBandRecord, connectionRecord)
    }

    private suspend fun processMessages(messages: List<String>, connectionRecord: ConnectionRecord) {
        val message = messages.firstOrNull { message ->
            val agentMessage = try {
                MessageSerializer.decodeFromString(message)
            } catch (e: Exception) {
                logger.warn("Cannot decode agent message: $message")
                null
            }
            agentMessage != null && agent.dispatcher.canHandleMessage(agentMessage)
        } ?: throw Exception("There is no message in requests~attach supported by agent.")

        agent.messageReceiver.receivePlaintextMessage(message, connectionRecord)
    }

    private suspend fun handleHandshakeReuse(
        outOfBandRecord: OutOfBandRecord,
        connectionRecord: ConnectionRecord,
    ): Boolean {
        val reuseMessage = agent.outOfBandService.createHandShakeReuse(outOfBandRecord, connectionRecord)
        val message = OutboundMessage(reuseMessage, connectionRecord)
        agent.messageSender.send(message)

        if (agent.outOfBandRepository.getById(outOfBandRecord.id).state != OutOfBandState.Done) {
            return agent.eventBus.waitFor<AgentEvents.OutOfBandEvent> { it.record.state == OutOfBandState.Done }
        }

        return true
    }

    private suspend fun findExistingConnection(outOfBandInvitation: OutOfBandInvitation): ConnectionRecord? {
        val invitationKey = outOfBandInvitation.invitationKey() ?: return null
        val connections = agent.connectionService.findAllByInvitationKey(invitationKey)

        if (connections.isEmpty()) {
            return null
        }
        return connections.firstOrNull { it.isReady() }
    }

    private suspend fun selectHandshakeProtocol(handshakeProtocols: List<HandshakeProtocol>): HandshakeProtocol? {
        if (handshakeProtocols.isEmpty()) {
            return null
        }

        val supportedProtocols = getSupportedHandshakeProtocols()
        if (handshakeProtocols.contains(agent.agentConfig.preferredHandshakeProtocol) &&
            supportedProtocols.contains(agent.agentConfig.preferredHandshakeProtocol)
        ) {
            return agent.agentConfig.preferredHandshakeProtocol
        }

        for (protocolName in handshakeProtocols) {
            if (supportedProtocols.contains(protocolName)) {
                return protocolName
            }
        }

        throw Exception("None of the provided handshake protocols $handshakeProtocols are supported. Supported protocols are $supportedProtocols")
    }
}
