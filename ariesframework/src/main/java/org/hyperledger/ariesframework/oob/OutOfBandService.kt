package org.hyperledger.ariesframework.oob

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.AgentEvents
import org.hyperledger.ariesframework.agent.MessageSerializer
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.hyperledger.ariesframework.oob.messages.HandshakeReuseAcceptedMessage
import org.hyperledger.ariesframework.oob.messages.HandshakeReuseMessage
import org.hyperledger.ariesframework.oob.models.OutOfBandRole
import org.hyperledger.ariesframework.oob.models.OutOfBandState
import org.hyperledger.ariesframework.oob.repository.OutOfBandRecord
import org.slf4j.LoggerFactory

class OutOfBandService(val agent: Agent) {
    private val outOfBandRepository = agent.outOfBandRepository
    private val logger = LoggerFactory.getLogger(OutOfBandService::class.java)

    suspend fun processHandshakeReuse(messageContext: InboundMessageContext): HandshakeReuseAcceptedMessage {
        val reuseMessage = MessageSerializer.decodeFromString(messageContext.plaintextMessage) as HandshakeReuseMessage

        val parentThreadId = reuseMessage.thread?.parentThreadId
            ?: throw Exception("handshake-reuse message must have a parent thread id")

        var outOfBandRecord = outOfBandRepository.findByInvitationId(parentThreadId)
            ?: throw Exception("No out of band record found for handshake-reuse message with parentThreadId: $parentThreadId")

        outOfBandRecord.assertRole(OutOfBandRole.Sender)
        outOfBandRecord.assertState(OutOfBandState.AwaitResponse)

        if (!outOfBandRecord.reusable) {
            updateState(outOfBandRecord, OutOfBandState.Done)
        }

        return HandshakeReuseAcceptedMessage(reuseMessage.threadId, parentThreadId)
    }

    suspend fun processHandshakeReuseAccepted(messageContext: InboundMessageContext) {
        val reuseAcceptedMessage = MessageSerializer.decodeFromString(messageContext.plaintextMessage) as HandshakeReuseAcceptedMessage

        val parentThreadId = reuseAcceptedMessage.thread?.parentThreadId
            ?: throw Exception("handshake-reuse-accepted message must have a parent thread id")

        var outOfBandRecord = outOfBandRepository.findByInvitationId(parentThreadId)
            ?: throw Exception("No out of band record found for handshake-reuse-accepted message  with parentThreadId: $parentThreadId")

        outOfBandRecord.assertRole(OutOfBandRole.Receiver)
        outOfBandRecord.assertState(OutOfBandState.PrepareResponse)

        val reusedConnection = messageContext.assertReadyConnection()
        if (outOfBandRecord.reuseConnectionId != reusedConnection.id) {
            throw Exception("handshake-reuse-accepted is not in response to a handshake-reuse message.")
        }

        updateState(outOfBandRecord, OutOfBandState.Done)
    }

    suspend fun createHandShakeReuse(outOfBandRecord: OutOfBandRecord, connectionRecord: ConnectionRecord): HandshakeReuseMessage {
        val reuseMessage = HandshakeReuseMessage(outOfBandRecord.outOfBandInvitation.id)

        outOfBandRecord.reuseConnectionId = connectionRecord.id
        outOfBandRepository.update(outOfBandRecord)

        return reuseMessage
    }

    suspend fun save(outOfBandRecord: OutOfBandRecord) {
        outOfBandRepository.save(outOfBandRecord)
    }

    suspend fun updateState(outOfBandRecord: OutOfBandRecord, newState: OutOfBandState) {
        outOfBandRecord.state = newState
        outOfBandRepository.update(outOfBandRecord)
        agent.eventBus.publish(AgentEvents.OutOfBandEvent(outOfBandRecord.copy()))
    }

    suspend fun findById(outOfBandRecordId: String): OutOfBandRecord? {
        return outOfBandRepository.findById(outOfBandRecordId)
    }

    suspend fun getById(outOfBandRecordId: String): OutOfBandRecord {
        return outOfBandRepository.getById(outOfBandRecordId)
    }

    suspend fun findByInvitationId(invitationId: String): OutOfBandRecord? {
        return outOfBandRepository.findByInvitationId(invitationId)
    }

    suspend fun findAllByInvitationKey(invitationKey: String): List<OutOfBandRecord> {
        return outOfBandRepository.findAllByInvitationKey(invitationKey)
    }

    suspend fun findByFingerprint(fingerprint: String): OutOfBandRecord? {
        return outOfBandRepository.findByFingerprint(fingerprint)
    }

    suspend fun getAll(): List<OutOfBandRecord> {
        return outOfBandRepository.getAll()
    }

    suspend fun deleteById(outOfBandId: String) {
        outOfBandRepository.deleteById(outOfBandId)
    }
}
