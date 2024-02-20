package org.hyperledger.ariesframework.credentials

import kotlinx.serialization.json.Json
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.Dispatcher
import org.hyperledger.ariesframework.agent.MessageSerializer
import org.hyperledger.ariesframework.credentials.handlers.CredentialAckHandler
import org.hyperledger.ariesframework.credentials.handlers.IssueCredentialHandler
import org.hyperledger.ariesframework.credentials.handlers.OfferCredentialHandler
import org.hyperledger.ariesframework.credentials.handlers.RequestCredentialHandler
import org.hyperledger.ariesframework.credentials.messages.CredentialAckMessage
import org.hyperledger.ariesframework.credentials.messages.IssueCredentialMessage
import org.hyperledger.ariesframework.credentials.messages.OfferCredentialMessage
import org.hyperledger.ariesframework.credentials.messages.ProposeCredentialMessage
import org.hyperledger.ariesframework.credentials.messages.RequestCredentialMessage
import org.hyperledger.ariesframework.credentials.models.AcceptCredentialOptions
import org.hyperledger.ariesframework.credentials.models.AcceptOfferOptions
import org.hyperledger.ariesframework.credentials.models.AcceptRequestOptions
import org.hyperledger.ariesframework.credentials.models.CreateOfferOptions
import org.hyperledger.ariesframework.credentials.models.CreateProposalOptions
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.slf4j.LoggerFactory

class CredentialsCommand(val agent: Agent, private val dispatcher: Dispatcher) {
    private val logger = LoggerFactory.getLogger(CredentialsCommand::class.java)

    init {
        registerHandlers(dispatcher)
        registerMessages()
    }

    private fun registerHandlers(dispatcher: Dispatcher) {
        dispatcher.registerHandler(CredentialAckHandler(agent))
        dispatcher.registerHandler(IssueCredentialHandler(agent))
        dispatcher.registerHandler(OfferCredentialHandler(agent))
        dispatcher.registerHandler(RequestCredentialHandler(agent))
    }

    private fun registerMessages() {
        MessageSerializer.registerMessage(CredentialAckMessage.type, CredentialAckMessage::class)
        MessageSerializer.registerMessage(IssueCredentialMessage.type, IssueCredentialMessage::class)
        MessageSerializer.registerMessage(OfferCredentialMessage.type, OfferCredentialMessage::class)
        MessageSerializer.registerMessage(ProposeCredentialMessage.type, ProposeCredentialMessage::class)
        MessageSerializer.registerMessage(RequestCredentialMessage.type, RequestCredentialMessage::class)
    }

    /**
     * Initiate a new credential exchange as holder by sending a credential proposal message
     * to the connection with the specified credential options.
     *
     * @param options options for the proposal.
     * @return credential record associated with the sent proposal message.
     */
    suspend fun proposeCredential(options: CreateProposalOptions): CredentialExchangeRecord {
        val (message, credentialRecord) = agent.credentialService.createProposal(options)
        agent.messageSender.send(OutboundMessage(message, options.connection))

        return credentialRecord
    }

    /**
     * Initiate a new credential exchange as issuer by sending a credential offer message
     * to the connection with the specified connection id.
     *
     * @param options options for the offer.
     * @return credential record associated with the sent credential offer message.
     */
    suspend fun offerCredential(options: CreateOfferOptions): CredentialExchangeRecord {
        val (message, credentialRecord) = agent.credentialService.createOffer(options)
        val connection = options.connection ?: throw Exception("Connection is required for sending credential offer")
        agent.messageSender.send(OutboundMessage(message, connection))

        return credentialRecord
    }

    /**
     * Accept a credential offer as holder (by sending a credential request message) to the connection
     * associated with the credential record.
     *
     * @param options options to accept the offer.
     * @return credential record associated with the sent credential request message.
     */
    suspend fun acceptOffer(options: AcceptOfferOptions): CredentialExchangeRecord {
        val message = agent.credentialService.createRequest(options)
        val credentialRecord = agent.credentialExchangeRepository.getById(options.credentialRecordId)
        val connection = agent.connectionRepository.getById(credentialRecord.connectionId)
        agent.messageSender.send(OutboundMessage(message, connection))

        return credentialRecord
    }

    /**
     * Declines a credential offer as holder (by sending a problem report message) to the connection
     *
     * @param options options to decline the offer.
     * @return credential record associated with the declined credential.
     */
    suspend fun declineOffer(options: AcceptOfferOptions): CredentialExchangeRecord {
        val message = agent.credentialService.createOfferDeclinedProblemReport(options)
        var credentialRecord = agent.credentialExchangeRepository.getById(options.credentialRecordId)
        val connection = agent.connectionRepository.getById(credentialRecord.connectionId)
        agent.messageSender.send(OutboundMessage(message, connection))
        return credentialRecord
    }

    /**
     * Accept a credential request as issuer (by sending a credential message) to the connection
     * associated with the credential record.
     *
     * @param options options to accept the request.
     * @return credential record associated with the sent credential message.
     */
    suspend fun acceptRequest(options: AcceptRequestOptions): CredentialExchangeRecord {
        val message = agent.credentialService.createCredential(options)
        val credentialRecord = agent.credentialExchangeRepository.getById(options.credentialRecordId)
        val connection = agent.connectionRepository.getById(credentialRecord.connectionId)
        agent.messageSender.send(OutboundMessage(message, connection))

        return credentialRecord
    }

    /**
     * Accept a credential as holder (by sending a credential acknowledgement message) to the connection
     * associated with the credential record.
     *
     * @param options options to accept the credential.
     * @return credential record associated with the sent credential acknowledgement message.
     */
    suspend fun acceptCredential(options: AcceptCredentialOptions): CredentialExchangeRecord {
        val message = agent.credentialService.createAck(options)
        val credentialRecord = agent.credentialExchangeRepository.getById(options.credentialRecordId)
        val connection = agent.connectionRepository.getById(credentialRecord.connectionId)
        agent.messageSender.send(OutboundMessage(message, connection))

        return credentialRecord
    }

    /**
     * Find a ``OfferCredentialMessage`` by credential record id.
     *
     * @param credentialRecordId: the id of the credential record.
     * @return the offer message associated with the credential record.
     */
    suspend fun findOfferMessage(credentialRecordId: String): OfferCredentialMessage? {
        val messageJson = agent.didCommMessageRepository.findAgentMessage(credentialRecordId, OfferCredentialMessage.type)

        return if (messageJson != null) {
            Json.decodeFromString<OfferCredentialMessage>(messageJson)
        } else {
            null
        }
    }

    /**
     * Find a ``RequestCredentialMessage`` by credential record id.
     *
     * @param credentialRecordId: the id of the credential record.
     * @return the request message associated with the credential record.
     */
    suspend fun findRequestMessage(credentialRecordId: String): RequestCredentialMessage? {
        val messageJson = agent.didCommMessageRepository.findAgentMessage(credentialRecordId, RequestCredentialMessage.type)

        return if (messageJson != null) {
            Json.decodeFromString<RequestCredentialMessage>(messageJson)
        } else {
            null
        }
    }

    /**
     * Find a ``IssueCredentialMessage`` by credential record id.
     *
     * @param credentialRecordId: the id of the credential record.
     * @return the credential message associated with the credential record.
     */
    suspend fun findCredentialMessage(credentialRecordId: String): IssueCredentialMessage? {
        val messageJson = agent.didCommMessageRepository.findAgentMessage(credentialRecordId, IssueCredentialMessage.type)

        return if (messageJson != null) {
            Json.decodeFromString<IssueCredentialMessage>(messageJson)
        } else {
            null
        }
    }
}
