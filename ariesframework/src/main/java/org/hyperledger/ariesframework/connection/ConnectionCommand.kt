package org.hyperledger.ariesframework.connection

import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.Dispatcher
import org.hyperledger.ariesframework.agent.MessageSerializer
import org.hyperledger.ariesframework.connection.handlers.ConnectionRequestHandler
import org.hyperledger.ariesframework.connection.handlers.ConnectionResponseHandler
import org.hyperledger.ariesframework.connection.handlers.DidExchangeCompleteHandler
import org.hyperledger.ariesframework.connection.handlers.DidExchangeRequestHandler
import org.hyperledger.ariesframework.connection.handlers.DidExchangeResponseHandler
import org.hyperledger.ariesframework.connection.handlers.TrustPingMessageHandler
import org.hyperledger.ariesframework.connection.messages.ConnectionInvitationMessage
import org.hyperledger.ariesframework.connection.messages.ConnectionRequestMessage
import org.hyperledger.ariesframework.connection.messages.ConnectionResponseMessage
import org.hyperledger.ariesframework.connection.messages.DidExchangeCompleteMessage
import org.hyperledger.ariesframework.connection.messages.DidExchangeRequestMessage
import org.hyperledger.ariesframework.connection.messages.DidExchangeResponseMessage
import org.hyperledger.ariesframework.connection.messages.TrustPingMessage
import org.hyperledger.ariesframework.connection.models.ConnectionState
import org.hyperledger.ariesframework.connection.models.didauth.DidDoc
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.hyperledger.ariesframework.oob.messages.OutOfBandInvitation
import org.hyperledger.ariesframework.oob.models.HandshakeProtocol
import org.hyperledger.ariesframework.oob.models.ReceiveOutOfBandInvitationConfig
import org.hyperledger.ariesframework.oob.repository.OutOfBandRecord
import org.slf4j.LoggerFactory

class ConnectionCommand(val agent: Agent, private val dispatcher: Dispatcher) {
    private val logger = LoggerFactory.getLogger(ConnectionCommand::class.java)

    init {
        registerHandlers(dispatcher)
        registerMessages()
    }

    private fun registerHandlers(dispatcher: Dispatcher) {
        dispatcher.registerHandler(ConnectionRequestHandler(agent))
        dispatcher.registerHandler(ConnectionResponseHandler(agent))
        dispatcher.registerHandler(TrustPingMessageHandler(agent))
        dispatcher.registerHandler(DidExchangeRequestHandler(agent))
        dispatcher.registerHandler(DidExchangeResponseHandler(agent))
        dispatcher.registerHandler(DidExchangeCompleteHandler(agent))
    }

    private fun registerMessages() {
        MessageSerializer.registerMessage(ConnectionInvitationMessage.type, ConnectionInvitationMessage::class)
        MessageSerializer.registerMessage(ConnectionRequestMessage.type, ConnectionRequestMessage::class)
        MessageSerializer.registerMessage(ConnectionResponseMessage.type, ConnectionResponseMessage::class)
        MessageSerializer.registerMessage(TrustPingMessage.type, TrustPingMessage::class)
        MessageSerializer.registerMessage(DidExchangeRequestMessage.type, DidExchangeRequestMessage::class)
        MessageSerializer.registerMessage(DidExchangeResponseMessage.type, DidExchangeResponseMessage::class)
        MessageSerializer.registerMessage(DidExchangeCompleteMessage.type, DidExchangeCompleteMessage::class)
    }

    /**
     * Create a new connection invitation message.
     *
     * @param autoAcceptConnection whether to auto accept the connection response.
     * @param alias alias to use for the connection.
     * @param multiUseInvitation whether to create a multi use invitation.
     * @param label label for the connection invitation.
     * @param imageUrl image url for the connection invitation.
     * @return `OutboundMessage` containing connection invitation message as payload.
     */
    suspend fun createConnection(
        autoAcceptConnection: Boolean? = null,
        alias: String? = null,
        multiUseInvitation: Boolean? = null,
        label: String? = null,
        imageUrl: String? = null,
    ): OutboundMessage {
        return agent.connectionService.createInvitation(
            agent.mediationRecipient.getRouting(),
            autoAcceptConnection,
            alias,
            multiUseInvitation,
            label,
            imageUrl,
        )
    }

    /**
     * Receive connection invitation as invitee and create connection. If auto accepting is enabled
     * via either the config passed in the function or the global agent config, a connection
     * request message will be send.
     * @param invitation optional connection invitation message to receive.
     * @param outOfBandInvitation optional out-of-band invitation to receive.
     * @param autoAcceptConnection whether to auto accept the connection response.
     * @param alias alias to use for the connection.
     * @return new connection record.
     */
    suspend fun receiveInvitation(
        invitation: ConnectionInvitationMessage? = null,
        outOfBandInvitation: OutOfBandInvitation? = null,
        autoAcceptConnection: Boolean? = null,
        alias: String? = null,
    ): ConnectionRecord {
        logger.debug("Receive connection invitation")
        var connection = agent.connectionService.processInvitation(
            invitation,
            outOfBandInvitation,
            agent.mediationRecipient.getRouting(),
            autoAcceptConnection,
            alias,
        )
        if (connection.autoAcceptConnection ?: agent.agentConfig.autoAcceptConnections) {
            connection = acceptInvitation(connection.id, autoAcceptConnection)
        }
        return connection
    }

    /**
     * Receive connection invitation as invitee and create connection. If auto accepting is enabled
     * via either the config passed in the function or the global agent config, a connection
     * request message will be send.
     * @param invitationUrl url containing a base64url encoded invitation to receive.
     * @param autoAcceptConnection whether to auto accept the connection response.
     * @param alias alias to use for the connection.
     * @return new connection record.
     */
    suspend fun receiveInvitationFromUrl(
        invitationUrl: String,
        autoAcceptConnection: Boolean? = null,
        alias: String? = null,
    ): ConnectionRecord {
        val invitation = ConnectionInvitationMessage.fromUrl(invitationUrl)
        return receiveInvitation(invitation, null, autoAcceptConnection, alias)
    }

    /**
     * Accept a connection invitation as invitee (by sending a connection request message) for the connection with the specified connection id.
     * This is not needed when auto accepting of connections is enabled.
     * @param connectionId id of the connection to accept.
     * @param autoAcceptConnection whether to auto accept the connection response.
     * @return new connection record.
     */
    suspend fun acceptInvitation(connectionId: String, autoAcceptConnection: Boolean? = null): ConnectionRecord {
        logger.debug("Accept connection invitation")
        val message = agent.connectionService.createRequest(connectionId, autoAcceptConnection = autoAcceptConnection)
        agent.messageSender.send(message)
        return message.connection
    }

    /**
     * Accept a connection invitation as invitee (by sending a connection request message) for the connection with the specified connection id.
     * This is not needed when auto accepting of connections is enabled.
     * @param outOfBandRecord out of band record containing the invitation to accept.
     * @param handshakeProtocol handshake protocol to use for accepting the invitation.
     * @param config optional config for accepting the invitation.
     * @return new connection record.
     */
    suspend fun acceptOutOfBandInvitation(
        outOfBandRecord: OutOfBandRecord,
        handshakeProtocol: HandshakeProtocol? = null,
        config: ReceiveOutOfBandInvitationConfig? = null,
    ): ConnectionRecord {
        val connection = receiveInvitation(
            null,
            outOfBandRecord.outOfBandInvitation,
            false,
            config?.alias,
        )

        if (handshakeProtocol == null) {
            val didDocServices = outOfBandRecord.outOfBandInvitation.services.mapNotNull { it.asDidCommService() }
            connection.theirDidDoc = connection.theirDidDoc ?: DidDoc(didDocServices)
            agent.connectionService.updateState(connection, ConnectionState.Complete)
            return connection
        }

        val message = if (handshakeProtocol == HandshakeProtocol.Connections) {
            agent.connectionService.createRequest(
                connection.id,
                config?.label,
                config?.imageUrl,
                config?.autoAcceptConnection,
            )
        } else {
            agent.didExchangeService.createRequest(
                connection.id,
                config?.label,
                config?.autoAcceptConnection,
            )
        }
        agent.messageSender.send(message)
        return message.connection
    }
}
