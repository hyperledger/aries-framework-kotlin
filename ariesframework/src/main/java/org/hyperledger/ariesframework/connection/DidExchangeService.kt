package org.hyperledger.ariesframework.connection

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.AgentEvents
import org.hyperledger.ariesframework.agent.MessageSerializer
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.agent.decorators.ThreadDecorator
import org.hyperledger.ariesframework.connection.messages.DidExchangeCompleteMessage
import org.hyperledger.ariesframework.connection.messages.DidExchangeRequestMessage
import org.hyperledger.ariesframework.connection.messages.DidExchangeResponseMessage
import org.hyperledger.ariesframework.connection.models.ConnectionRole
import org.hyperledger.ariesframework.connection.models.ConnectionState
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.hyperledger.ariesframework.decodeBase64
import org.hyperledger.ariesframework.oob.repository.OutOfBandRecord
import org.hyperledger.ariesframework.util.DIDParser
import org.slf4j.LoggerFactory

class DidExchangeService(val agent: Agent) {
    val connectionRepository = agent.connectionRepository
    private val logger = LoggerFactory.getLogger(DidExchangeService::class.java)

    /**
     * Create a DID exchange request message for the connection with the specified connection id.
     *
     * @param connectionId the id of the connection for which to create a DID exchange request.
     * @param label the label to use for the DID exchange request.
     * @param autoAcceptConnection whether to automatically accept the DID exchange response.
     * @return the outbound message containing DID exchange request.
     */
    suspend fun createRequest(
        connectionId: String,
        label: String? = null,
        autoAcceptConnection: Boolean? = null,
    ): OutboundMessage {
        var connectionRecord = connectionRepository.getById(connectionId)
        assert(connectionRecord.state == ConnectionState.Invited)
        assert(connectionRecord.role == ConnectionRole.Invitee)

        val peerDid = agent.peerDIDService.createPeerDID(connectionRecord.verkey)
        logger.debug("Created peer DID for a RequestMessage: $peerDid")
        val message = DidExchangeRequestMessage(label ?: agent.agentConfig.label, did = peerDid)

        if (autoAcceptConnection != null) {
            connectionRecord.autoAcceptConnection = autoAcceptConnection
        }
        connectionRecord.threadId = message.id
        connectionRecord.did = peerDid
        updateState(connectionRecord, ConnectionState.Requested)

        return OutboundMessage(message, connectionRecord)
    }

    /**
     * Process a received DID exchange request message. This will not accept the DID exchange request
     * or send a DID exchange response message. It will only update the existing connection record
     * with all the new information from the DID exchange request message. Use [createResponse()]
     * after calling this function to create a DID exchange response.
     *
     * @param messageContext the message context containing the DID exchange request message.
     * @return updated connection record.
     */
    suspend fun processRequest(messageContext: InboundMessageContext): ConnectionRecord {
        val message = MessageSerializer.decodeFromString(messageContext.plaintextMessage) as DidExchangeRequestMessage
        val recipientKey = messageContext.recipientVerkey
            ?: throw Exception("Unable to process connection request without recipientVerkey")

        var outOfBandRecord: OutOfBandRecord?
        val outOfBandRecords = agent.outOfBandService.findAllByInvitationKey(recipientKey)
        if (outOfBandRecords.isEmpty()) {
            throw Exception("No out-of-band record or connection record found for invitation key: $recipientKey")
        } else {
            outOfBandRecord = outOfBandRecords[0]
        }

        val didDoc = agent.peerDIDService.parsePeerDID(message.did)
        var connectionRecord = agent.connectionService.createConnection(
            ConnectionRole.Inviter,
            ConnectionState.Invited,
            null,
            outOfBandRecord!!.outOfBandInvitation,
            null,
            agent.mediationRecipient.getRouting(),
            message.label,
            outOfBandRecord!!.autoAcceptConnection,
            true,
            null,
            null,
            message.threadId,
        )

        connectionRepository.save(connectionRecord)

        connectionRecord.theirDidDoc = didDoc
        connectionRecord.theirLabel = message.label
        connectionRecord.threadId = message.id
        connectionRecord.theirDid = didDoc.id

        if (connectionRecord.theirKey() == null) {
            throw Exception("Connection with id ${connectionRecord.id} has no recipient keys.")
        }

        updateState(connectionRecord, ConnectionState.Requested)
        return connectionRecord
    }

    /**
     * Create a DID exchange response message for the connection with the specified connection id.
     *
     * @param connectionId the id of the connection for which to create a DID exchange response.
     * @return outbound message containing DID exchange response.
     */
    suspend fun createResponse(connectionId: String): OutboundMessage {
        var connectionRecord = connectionRepository.getById(connectionId)
        assert(connectionRecord.state == ConnectionState.Requested)
        assert(connectionRecord.role == ConnectionRole.Inviter)
        val threadId = connectionRecord.threadId
            ?: throw Exception("Connection record with id ${connectionRecord.id} has no thread id.")

        val peerDid = agent.peerDIDService.createPeerDID(connectionRecord.verkey)
        connectionRecord.did = peerDid

        val message = DidExchangeResponseMessage(threadId, peerDid)
        message.thread = ThreadDecorator(threadId)

        val payload = peerDid.toByteArray()
        val signingKey = connectionRecord.getTags()["invitationKey"] ?: connectionRecord.verkey
        val jws = agent.jwsService.createJws(payload, signingKey)
        var attachment = Attachment.fromData(payload)
        attachment.addJws(jws)
        message.didRotate = attachment

        updateState(connectionRecord, ConnectionState.Responded)

        return OutboundMessage(message, connectionRecord)
    }

    /**
     * Process a received DID exchange response message. This will not accept the DID exchange response
     * or send a DID exchange complete message. It will only update the existing connection record
     * with all the new information from the DID exchange response message. Use [createComplete()]
     * after calling this function to create a DID exchange complete message.
     *
     * @param messageContext the message context containing a DID exchange response message.
     * @return updated connection record.
     */
    suspend fun processResponse(messageContext: InboundMessageContext): ConnectionRecord {
        val message = MessageSerializer.decodeFromString(messageContext.plaintextMessage) as DidExchangeResponseMessage
        var connectionRecord = try {
            agent.connectionService.getByThreadId(message.threadId)
        } catch (e: Exception) {
            throw Exception("Unable to process DID exchange response: connection for threadId: ${message.threadId} not found")
        }
        assert(connectionRecord.state == ConnectionState.Requested)
        assert(connectionRecord.role == ConnectionRole.Invitee)

        if (message.threadId != connectionRecord.threadId) {
            throw Exception("Invalid or missing thread ID")
        }

        verifyDidRotate(message, connectionRecord)

        val didDoc = agent.peerDIDService.parsePeerDID(message.did)
        connectionRecord.theirDid = didDoc.id
        connectionRecord.theirDidDoc = didDoc

        updateState(connectionRecord, ConnectionState.Responded)
        return connectionRecord
    }

    private fun verifyDidRotate(message: DidExchangeResponseMessage, connectionRecord: ConnectionRecord) {
        val didRotateAttachment = message.didRotate
            ?: throw Exception("Missing valid did_rotate in response: ${message.didRotate}")
        val jws = didRotateAttachment.data.jws
            ?: throw Exception("Missing valid jws in did_rotate attachment: ${didRotateAttachment.data.jws}")
        val base64Payload = didRotateAttachment.data.base64
            ?: throw Exception("Missing valid base64 in did_rotate attachment: ${didRotateAttachment.data.base64}")
        val payload = base64Payload.decodeBase64()

        val signedDid = payload.decodeToString()
        if (message.did != signedDid) {
            throw Exception("DID Rotate attachment's did $signedDid does not correspond to message did ${message.did}")
        }

        val (isValid, signer) = agent.jwsService.verifyJws(jws, payload)
        val senderKeys = connectionRecord.outOfBandInvitation!!.fingerprints().map {
            DIDParser.convertFingerprintToVerkey(it)
        }
        if (!isValid || !senderKeys.contains(signer)) {
            throw Exception("Failed to verify did rotate signature. isValid: $isValid, signer: $signer, senderKeys: $senderKeys")
        }
    }

    /**
     * Create a DID exchange complete message for the connection with the specified connection id.
     *
     * @param connectionId the id of the connection for which to create a DID exchange complete message.
     * @return outbound message containing a DID exchange complete message.
     */
    suspend fun createComplete(connectionId: String): OutboundMessage {
        var connectionRecord = connectionRepository.getById(connectionId)
        assert(connectionRecord.state == ConnectionState.Responded)

        val threadId = connectionRecord.threadId
            ?: throw Exception("Connection record with id ${connectionRecord.id} has no thread id.")
        val parentThreadId = connectionRecord.outOfBandInvitation?.id
            ?: throw Exception("Connection record with id ${connectionRecord.id} has no parent thread id.")

        val message = DidExchangeCompleteMessage(threadId, parentThreadId)
        updateState(connectionRecord, ConnectionState.Complete)

        return OutboundMessage(message, connectionRecord)
    }

    private suspend fun updateState(connectionRecord: ConnectionRecord, newState: ConnectionState) {
        connectionRecord.state = newState
        connectionRepository.update(connectionRecord)
        agent.eventBus.publish(AgentEvents.ConnectionEvent(connectionRecord.copy()))
    }
}
