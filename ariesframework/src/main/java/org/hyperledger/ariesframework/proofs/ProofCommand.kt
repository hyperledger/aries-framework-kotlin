package org.hyperledger.ariesframework.proofs

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.Dispatcher
import org.hyperledger.ariesframework.agent.MessageSerializer
import org.hyperledger.ariesframework.proofs.handlers.PresentationAckHandler
import org.hyperledger.ariesframework.proofs.handlers.PresentationHandler
import org.hyperledger.ariesframework.proofs.handlers.RequestPresentationHandler
import org.hyperledger.ariesframework.proofs.messages.PresentationAckMessage
import org.hyperledger.ariesframework.proofs.messages.PresentationMessage
import org.hyperledger.ariesframework.proofs.messages.RequestPresentationMessage
import org.hyperledger.ariesframework.proofs.models.AutoAcceptProof
import org.hyperledger.ariesframework.proofs.models.ProofRequest
import org.hyperledger.ariesframework.proofs.models.RequestedCredentials
import org.hyperledger.ariesframework.proofs.models.RetrievedCredentials
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord
import org.slf4j.LoggerFactory

class ProofCommand(val agent: Agent, private val dispatcher: Dispatcher) {
    private val logger = LoggerFactory.getLogger(ProofCommand::class.java)

    init {
        registerHandlers(dispatcher)
        registerMessages()
    }

    private fun registerHandlers(dispatcher: Dispatcher) {
        dispatcher.registerHandler(RequestPresentationHandler(agent))
        dispatcher.registerHandler(PresentationHandler(agent))
        dispatcher.registerHandler(PresentationAckHandler(agent))
    }

    private fun registerMessages() {
        MessageSerializer.registerMessage(RequestPresentationMessage.type, RequestPresentationMessage::class)
        MessageSerializer.registerMessage(PresentationMessage.type, PresentationMessage::class)
        MessageSerializer.registerMessage(PresentationAckMessage.type, PresentationAckMessage::class)
    }

    /**
     * Initiate a new presentation exchange as verifier by sending a presentation request message
     * to the connection with the specified connection id.
     *
     * @param connectionId the connection to send the proof request to.
     * @param proofRequest the proof request to send.
     * @param comment a comment to include in the proof request message.
     * @param autoAcceptProof whether to automatically accept the proof message.
     * @return a new proof record for the proof exchange.
     */
    suspend fun requestProof(
        connectionId: String,
        proofRequest: ProofRequest,
        comment: String? = null,
        autoAcceptProof: AutoAcceptProof? = null,
    ): ProofExchangeRecord {
        val connection = agent.connectionRepository.getById(connectionId)
        val (message, record) = agent.proofService.createRequest(
            proofRequest,
            connection,
            comment,
            autoAcceptProof,
        )

        agent.messageSender.send(OutboundMessage(message, connection))

        return record
    }

    /**
     * Accept a presentation request as prover (by sending a presentation message) to the connection
     * associated with the proof record.
     *
     * @param proofRecordId the id of the proof record for which to accept the request.
     * @param requestedCredentials the requested credentials object specifying which credentials to use for the proof.
     * @param comment a comment to include in the presentation message.
     * @return proof record associated with the sent presentation message.
     */
    suspend fun acceptRequest(
        proofRecordId: String,
        requestedCredentials: RequestedCredentials,
        comment: String? = null,
    ): ProofExchangeRecord {
        val record = agent.proofRepository.getById(proofRecordId)
        val (message, proofRecord) = agent.proofService.createPresentation(
            record,
            requestedCredentials,
            comment,
        )

        val connection = agent.connectionRepository.getById(record.connectionId)
        agent.messageSender.send(OutboundMessage(message, connection))

        return proofRecord
    }

    /**
     * Decline a presentation request as prover (by sending a problem report message) to the connection
     * associated with the proof record.
     *
     * @param proofRecordId the id of the proof record for which to decline the request.
     * @return proof record associated with the sent presentation request message.
     */
    suspend fun declineRequest(
        proofRecordId: String,
    ): ProofExchangeRecord {
        val record = agent.proofRepository.getById(proofRecordId)
        val (message, proofRecord) = agent.proofService.createPresentationDeclinedProblemReport(record)

        val connection = agent.connectionRepository.getById(record.connectionId)
        agent.messageSender.send(OutboundMessage(message, connection))

        return proofRecord
    }

    /**
     * Accept a presentation as verifier (by sending a presentation acknowledgement message) to the connection
     * associated with the proof record.
     *
     * @param proofRecordId the id of the proof record for which to accept the presentation.
     * @return proof record associated with the sent presentation acknowledgement message.
     */
    suspend fun acceptPresentation(proofRecordId: String): ProofExchangeRecord {
        val record = agent.proofRepository.getById(proofRecordId)
        val connection = agent.connectionRepository.getById(record.connectionId)
        val (message, proofRecord) = agent.proofService.createAck(record)
        agent.messageSender.send(OutboundMessage(message, connection))
        return proofRecord
    }

    /**
     * Create a [RetrievedCredentials] object. Given input proof request,
     * use credentials in the wallet to build indy requested credentials object for proof creation.
     *
     * @param proofRecordId the id of the proof request to get the matching credentials for.
     * @return [RetrievedCredentials] object.
     */
    suspend fun getRequestedCredentialsForProofRequest(proofRecordId: String): RetrievedCredentials {
        val record = agent.proofRepository.getById(proofRecordId)
        val proofRequestMessageJson = agent.didCommMessageRepository.getAgentMessage(
            record.id,
            RequestPresentationMessage.type,
        )
        val proofRequestMessage = MessageSerializer.decodeFromString(proofRequestMessageJson) as RequestPresentationMessage

        val proofRequestJson = proofRequestMessage.indyProofRequest()
        logger.debug("Proof request json: $proofRequestJson")
        val proofRequest = Json.decodeFromString<ProofRequest>(proofRequestJson)

        return agent.proofService.getRequestedCredentialsForProofRequest(proofRequest)
    }
}
