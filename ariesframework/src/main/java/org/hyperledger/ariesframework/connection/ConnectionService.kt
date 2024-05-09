package org.hyperledger.ariesframework.connection

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.Tags
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.AgentEvents
import org.hyperledger.ariesframework.agent.MessageSerializer
import org.hyperledger.ariesframework.agent.decorators.SignatureDecorator
import org.hyperledger.ariesframework.agent.decorators.ThreadDecorator
import org.hyperledger.ariesframework.connection.messages.ConnectionInvitationMessage
import org.hyperledger.ariesframework.connection.messages.ConnectionRequestMessage
import org.hyperledger.ariesframework.connection.messages.ConnectionResponseMessage
import org.hyperledger.ariesframework.connection.messages.TrustPingMessage
import org.hyperledger.ariesframework.connection.models.Connection
import org.hyperledger.ariesframework.connection.models.ConnectionRole
import org.hyperledger.ariesframework.connection.models.ConnectionState
import org.hyperledger.ariesframework.connection.models.didauth.DidDoc
import org.hyperledger.ariesframework.connection.models.didauth.IndyAgentService
import org.hyperledger.ariesframework.connection.models.didauth.ReferencedAuthentication
import org.hyperledger.ariesframework.connection.models.didauth.didDocServiceModule
import org.hyperledger.ariesframework.connection.models.didauth.publicKey.Ed25119Sig2018
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.hyperledger.ariesframework.oob.messages.OutOfBandInvitation
import org.hyperledger.ariesframework.oob.repository.OutOfBandRecord
import org.hyperledger.ariesframework.routing.Routing
import org.slf4j.LoggerFactory

class ConnectionService(val agent: Agent) {
    val connectionRepository = agent.connectionRepository
    private val logger = LoggerFactory.getLogger(ConnectionService::class.java)

    fun createConnection(
        role: ConnectionRole,
        state: ConnectionState,
        invitation: ConnectionInvitationMessage? = null,
        outOfBandInvitation: OutOfBandInvitation? = null,
        alias: String?,
        routing: Routing,
        theirLabel: String?,
        autoAcceptConnection: Boolean?,
        multiUseInvitation: Boolean,
        tags: Tags? = null,
        imageUrl: String?,
        threadId: String?,
    ): ConnectionRecord {
        val publicKey = Ed25119Sig2018(
            "${routing.did}#1",
            routing.did,
            routing.verkey,
        )

        val services = routing.endpoints.mapIndexed { index, endpoint ->
            IndyAgentService(
                "${routing.did}#IndyAgentService",
                endpoint,
                listOf(routing.verkey),
                routing.routingKeys,
                index,
            )
        }

        val auth = ReferencedAuthentication(
            Ed25119Sig2018.type,
            publicKey.id,
        )

        val didDoc = DidDoc(
            id = routing.did,
            publicKey = listOf(publicKey),
            service = services,
            authentication = listOf(auth),
        )

        return ConnectionRecord(
            _tags = tags,
            state = state,
            role = role,
            didDoc = didDoc,
            did = routing.did,
            verkey = routing.verkey,
            theirLabel = theirLabel,
            invitation = invitation,
            outOfBandInvitation = outOfBandInvitation,
            alias = alias,
            autoAcceptConnection = autoAcceptConnection,
            imageUrl = imageUrl,
            multiUseInvitation = multiUseInvitation,
            mediatorId = routing.mediatorId,
        )
    }

    /**
     * Create a new connection record containing a connection invitation message.
     * @param routing routing information for the connection.
     * @param autoAcceptConnection whether to auto accept the connection request.
     * @param alias alias for the connection.
     * @param multiUseInvitation allow the creation of a reusable invitation.
     * @param label label for the connection.
     * @param imageUrl image url for the connection.
     * @return outbound message containing connection invitation.
     */
    suspend fun createInvitation(
        routing: Routing,
        autoAcceptConnection: Boolean? = null,
        alias: String? = null,
        multiUseInvitation: Boolean? = null,
        label: String? = null,
        imageUrl: String? = null,
    ): OutboundMessage {
        var connectionRecord = createConnection(
            ConnectionRole.Inviter,
            ConnectionState.Invited,
            null,
            null,
            alias,
            routing,
            null,
            autoAcceptConnection,
            multiUseInvitation ?: false,
            null,
            null,
            null,
        )

        val service = connectionRecord.didDoc.didCommServices()[0]
        val invitation = ConnectionInvitationMessage(
            label = label ?: agent.agentConfig.label,
            imageUrl = imageUrl ?: agent.agentConfig.connectionImageUrl,
            recipientKeys = service.recipientKeys,
            serviceEndpoint = service.serviceEndpoint,
            routingKeys = service.routingKeys,
        )

        connectionRecord.invitation = invitation
        connectionRepository.save(connectionRecord)

        agent.eventBus.publish(AgentEvents.ConnectionEvent(connectionRecord.copy()))
        return OutboundMessage(invitation, connectionRecord)
    }

    /**
     * Process a received invitation message. The invitation message should be either a connection
     * invitation or an out of band invitation. This will not accept the invitation
     * or send an invitation request message. It will only create a connection record
     * with all the information about the invitation stored.
     * Use [createRequest] after calling this function to create a connection request.
     *
     * @param invitation optional invitation message to process.
     * @param outOfBandInvitation optional out-of-band invitation message to process.
     * @param routing routing information for the connection.
     * @param autoAcceptConnection whether to auto accept the connection response.
     * @param alias alias for the connection.
     * @return new connection record.
     */
    suspend fun processInvitation(
        invitation: ConnectionInvitationMessage? = null,
        outOfBandInvitation: OutOfBandInvitation? = null,
        routing: Routing,
        autoAcceptConnection: Boolean? = null,
        alias: String? = null,
    ): ConnectionRecord {
        require((invitation != null || outOfBandInvitation != null) && (invitation == null || outOfBandInvitation == null)) {
            "Either invitation or outOfBandInvitation must be set, but not both."
        }
        require(outOfBandInvitation == null || outOfBandInvitation.invitationKey() != null) {
            "Out of band invitation must have an invitation key."
        }

        val connectionRecord = createConnection(
            ConnectionRole.Invitee,
            ConnectionState.Invited,
            invitation,
            outOfBandInvitation,
            alias,
            routing,
            invitation?.label ?: outOfBandInvitation?.label,
            autoAcceptConnection,
            false,
            null,
            invitation?.imageUrl ?: outOfBandInvitation?.imageUrl,
            null,
        )

        connectionRepository.save(connectionRecord)

        agent.eventBus.publish(AgentEvents.ConnectionEvent(connectionRecord.copy()))
        return connectionRecord
    }

    /**
     * Create a connection request message for the connection with the specified connection id.
     * @param connectionId the id of the connection for which to create a connection request.
     * @param label the label to use for the connection request.
     * @param imageUrl the image url to use for the connection request.
     * @param autoAcceptConnection whether to automatically accept the connection response.
     * @return outbound message containing connection request.
     */
    suspend fun createRequest(
        connectionId: String,
        label: String? = null,
        imageUrl: String? = null,
        autoAcceptConnection: Boolean? = null,
    ): OutboundMessage {
        logger.debug("Creating connection request for connection: $connectionId")
        var connectionRecord = connectionRepository.getById(connectionId)
        assert(connectionRecord.state == ConnectionState.Invited)
        assert(connectionRecord.role == ConnectionRole.Invitee)

        val connectionRequest = ConnectionRequestMessage(
            connectionId,
            label ?: agent.agentConfig.label,
            imageUrl ?: agent.agentConfig.connectionImageUrl,
            Connection(connectionRecord.did, connectionRecord.didDoc),
        )

        if (autoAcceptConnection != null) {
            connectionRecord.autoAcceptConnection = autoAcceptConnection
        }
        connectionRecord.threadId = connectionRequest.id
        updateState(connectionRecord, ConnectionState.Requested)

        return OutboundMessage(connectionRequest, connectionRecord)
    }

    /**
     * Process a received connection request message. This will not accept the connection request
     * or send a connection response message. It will only update the existing connection record
     * with all the new information from the connection request message. Use [createResponse]
     * after calling this function to create a connection response.
     *
     * @param messageContext the message context containing the connection request message.
     * @return updated connection record.
     */
    suspend fun processRequest(messageContext: InboundMessageContext): ConnectionRecord {
        logger.debug("Processing connection request message")
        val message = MessageSerializer.decodeFromString(messageContext.plaintextMessage) as ConnectionRequestMessage

        val recipientKey = messageContext.recipientVerkey ?: throw Exception("Unable to process connection request without recipientVerkey")
        val senderKey = messageContext.senderVerkey ?: throw Exception("Unable to process connection request without senderVerkey")

        if (message.connection.didDoc == null) {
            throw Exception("Public DIDs are not supported yet")
        }

        var connectionRecord = findByKeys(senderKey, recipientKey)
        var outOfBandRecord: OutOfBandRecord? = null
        if (connectionRecord == null) {
            val outOfBandRecords = agent.outOfBandService.findAllByInvitationKey(recipientKey)
            if (outOfBandRecords.isEmpty()) {
                connectionRecord = findByInvitationKey(recipientKey)
                if (connectionRecord == null) {
                    throw Exception("No out-of-band record or connection record found for invitation key: $recipientKey")
                }
            } else {
                outOfBandRecord = outOfBandRecords[0]
            }
        }

        if (connectionRecord == null || connectionRecord.multiUseInvitation) {
            connectionRecord = createConnection(
                ConnectionRole.Inviter,
                ConnectionState.Invited,
                connectionRecord?.invitation,
                outOfBandRecord?.outOfBandInvitation,
                null,
                agent.mediationRecipient.getRouting(),
                message.label,
                connectionRecord?.autoAcceptConnection ?: outOfBandRecord?.autoAcceptConnection,
                true,
                null,
                message.imageUrl,
                message.threadId,
            )

            connectionRepository.save(connectionRecord)
        }

        connectionRecord.theirDidDoc = message.connection.didDoc
        connectionRecord.theirLabel = message.label
        connectionRecord.threadId = message.id
        connectionRecord.theirDid = message.connection.did
        connectionRecord.imageUrl = message.imageUrl

        if (connectionRecord.theirKey() == null) {
            throw Exception("Connection with id ${connectionRecord.id} has no recipient keys.")
        }

        updateState(connectionRecord, ConnectionState.Requested)
        return connectionRecord
    }

    /**
     * Create a connection response message for the connection with the specified connection id.
     * @param connectionId the id of the connection for which to create a connection response.
     * @return outbound message containing connection response.
     */
    suspend fun createResponse(connectionId: String): OutboundMessage {
        logger.debug("Creating connection response for connection: $connectionId")
        var connectionRecord = connectionRepository.getById(connectionId)
        logger.debug("Connection record: {}", Json.encodeToString(connectionRecord))
        assert(connectionRecord.state == ConnectionState.Requested)
        assert(connectionRecord.role == ConnectionRole.Inviter)
        val threadId = connectionRecord.threadId ?: throw Exception("Connection record with id ${connectionRecord.id} has no thread id.")

        val connection = Connection(connectionRecord.did, connectionRecord.didDoc)
        val connectionJson = Json { serializersModule = didDocServiceModule }.encodeToString(connection)

        val signingKey = connectionRecord.getTags()["invitationKey"] ?: connectionRecord.verkey
        val signature = SignatureDecorator.signData(connectionJson.toByteArray(), agent.wallet, signingKey)
        val connectionResponse = ConnectionResponseMessage(signature)
        connectionResponse.thread = ThreadDecorator(threadId)

        updateState(connectionRecord, ConnectionState.Responded)
        return OutboundMessage(connectionResponse, connectionRecord)
    }

    suspend fun processResponse(messageContext: InboundMessageContext): ConnectionRecord {
        logger.debug("Processing connection response message")
        val message = MessageSerializer.decodeFromString(messageContext.plaintextMessage) as ConnectionResponseMessage

        val connectionRecord = try {
            getByThreadId(message.threadId)
        } catch (e: Exception) {
            throw Exception("Connection record with thread id ${message.threadId} not found")
        }

        assert(connectionRecord.state == ConnectionState.Requested)
        assert(connectionRecord.role == ConnectionRole.Invitee)

        val connection = message.connectionSig.unpackConnection()

        if (connectionRecord.getTags()["invitationKey"] != message.connectionSig.signer) {
            throw Exception(
                "Connection object in connection response message is not signed with same key as recipient key in invitation" +
                    " expected=${connectionRecord.getTags()["invitationKey"]} received=${message.connectionSig.signer}",
            )
        }

        connectionRecord.theirDid = connection.did
        connectionRecord.theirDidDoc = connection.didDoc
        connectionRecord.threadId = message.threadId

        updateState(connectionRecord, ConnectionState.Responded)
        return connectionRecord
    }

    /**
     * Create a trust ping message for the connection with the specified connection id.
     * By default a trust ping message should elicit a response. If this is not desired the
     * `responseRequested` parameter can be set to `false`.
     * @param connectionId the id of the connection for which to create a trust ping message.
     * @param responseRequested whether to request a response from the recipient. Default is true.
     * @param comment the comment to include in the trust ping message.
     * @return outbound message containing trust ping message.
     */
    suspend fun createTrustPing(connectionId: String, responseRequested: Boolean? = null, comment: String? = null): OutboundMessage {
        var connectionRecord = connectionRepository.getById(connectionId)
        assert(connectionRecord.state == ConnectionState.Responded || connectionRecord.state == ConnectionState.Complete)
        val trustPing = TrustPingMessage(comment, responseRequested ?: true)

        if (connectionRecord.state != ConnectionState.Complete) {
            updateState(connectionRecord, ConnectionState.Complete)
        }

        return OutboundMessage(trustPing, connectionRecord)
    }

    suspend fun updateState(connectionRecord: ConnectionRecord, newState: ConnectionState) {
        connectionRecord.state = newState
        connectionRepository.update(connectionRecord)
        agent.eventBus.publish(AgentEvents.ConnectionEvent(connectionRecord.copy()))
    }

    suspend fun fetchState(connectionRecord: ConnectionRecord): ConnectionState {
        if (connectionRecord.state == ConnectionState.Complete) {
            return connectionRecord.state
        }

        val connection = connectionRepository.getById(connectionRecord.id)
        return connection.state
    }

    /**
     * Find a connection by invitation key. If there are multiple connections with the same invitation key,
     * the first one will be returned.
     *
     * @param key the invitation key to search for.
     * @return the connection record, if found.
     */
    suspend fun findByInvitationKey(key: String): ConnectionRecord? {
        val connections = connectionRepository.findByQuery(
            """
            {"invitationKey": "$key"}
            """,
        )
        if (connections.isEmpty()) {
            return null
        }
        return connections[0]
    }

    /**
     * Find all connections by invitation key.
     *
     * @param key the invitation key to search for.
     * @return the connection record, if found.
     */
    suspend fun findAllByInvitationKey(key: String): List<ConnectionRecord> {
        return connectionRepository.findByQuery(
            """
            {"invitationKey": "$key"}
            """,
        )
    }

    /**
     * Retrieve a connection record by thread id.
     *
     * @param threadId the thread id.
     * @return the connection record.
     */
    suspend fun getByThreadId(threadId: String): ConnectionRecord {
        return connectionRepository.getSingleByQuery(
            """
            {"threadId": "$threadId"}
            """,
        )
    }

    /**
     * Find connection by sender key and recipient key.
     *
     * @param senderKey the sender key of the received message.
     * @param recipientKey the recipient key of the received message, which is this agent's verkey.
     * @return the connection record, if found.
     */
    suspend fun findByKeys(senderKey: String, recipientKey: String): ConnectionRecord? {
        return connectionRepository.findSingleByQuery(
            """
            {"verkey": "$recipientKey", "theirKey": "$senderKey"}
            """,
        )
    }
}
