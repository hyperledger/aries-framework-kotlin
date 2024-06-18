package org.hyperledger.ariesframework.credentials

import anoncreds_uniffi.Credential
import anoncreds_uniffi.CredentialDefinition
import anoncreds_uniffi.CredentialDefinitionPrivate
import anoncreds_uniffi.CredentialKeyCorrectnessProof
import anoncreds_uniffi.CredentialOffer
import anoncreds_uniffi.CredentialRequest
import anoncreds_uniffi.CredentialRequestMetadata
import anoncreds_uniffi.CredentialRevocationConfig
import anoncreds_uniffi.Issuer
import anoncreds_uniffi.Prover
import anoncreds_uniffi.RevocationRegistryDefinition
import anoncreds_uniffi.RevocationRegistryDefinitionPrivate
import anoncreds_uniffi.RevocationStatusList
import anoncreds_uniffi.Schema
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.hyperledger.ariesframework.AckStatus
import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.AgentEvents
import org.hyperledger.ariesframework.agent.MessageSerializer
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.agent.decorators.ThreadDecorator
import org.hyperledger.ariesframework.anoncreds.storage.CredentialRecord
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
import org.hyperledger.ariesframework.credentials.models.CredentialPreview
import org.hyperledger.ariesframework.credentials.models.CredentialState
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.credentials.repository.CredentialRecordBinding
import org.hyperledger.ariesframework.problemreports.messages.CredentialProblemReportMessage
import org.hyperledger.ariesframework.storage.BaseRecord
import org.hyperledger.ariesframework.storage.DidCommMessageRole
import org.slf4j.LoggerFactory
import java.util.UUID

class CredentialService(val agent: Agent) {
    private val logger = LoggerFactory.getLogger(CredentialService::class.java)
    private val ledgerService = agent.ledgerService
    private val credentialExchangeRepository = agent.credentialExchangeRepository

    /**
     * Create a ``ProposeCredentialMessage`` not bound to an existing credential record.
     *
     * @param options options for the proposal.
     * @return proposal message and associated credential record.
     */
    suspend fun createProposal(options: CreateProposalOptions): Pair<ProposeCredentialMessage, CredentialExchangeRecord> {
        val credentialRecord = CredentialExchangeRecord(
            connectionId = options.connection.id,
            threadId = BaseRecord.generateId(),
            state = CredentialState.ProposalSent,
            autoAcceptCredential = options.autoAcceptCredential,
            protocolVersion = "v1",
        )

        val message = ProposeCredentialMessage(
            options.comment,
            options.credentialPreview,
            options.schemaIssuerDid,
            options.schemaId,
            options.schemaName,
            options.schemaVersion,
            options.credentialDefinitionId,
            options.issuerDid,
        )
        message.id = credentialRecord.threadId

        agent.didCommMessageRepository.saveAgentMessage(DidCommMessageRole.Sender, message, credentialRecord.id)

        credentialExchangeRepository.save(credentialRecord)
        agent.eventBus.publish(AgentEvents.CredentialEvent(credentialRecord.copy()))

        return Pair(message, credentialRecord)
    }

    /**
     * Create a ``OfferCredentialMessage`` not bound to an existing credential record.
     *
     * @param options options for the offer.
     * @return offer message and associated credential record.
     */
    suspend fun createOffer(options: CreateOfferOptions): Pair<OfferCredentialMessage, CredentialExchangeRecord> {
        if (options.connection == null) {
            logger.info("Creating credential offer without connection. This should be used for out-of-band request message with handshake.")
        }
        val credentialRecord = CredentialExchangeRecord(
            connectionId = options.connection?.id ?: "connectionless-offer",
            threadId = BaseRecord.generateId(),
            state = CredentialState.OfferSent,
            autoAcceptCredential = options.autoAcceptCredential,
            protocolVersion = "v1",
        )

        val credentialDefinitionRecord = agent.credentialDefinitionRepository.getByCredDefId(options.credentialDefinitionId)
        val offer = Issuer().createCredentialOffer(
            credentialDefinitionRecord.schemaId,
            credentialDefinitionRecord.credDefId,
            CredentialKeyCorrectnessProof(credentialDefinitionRecord.keyCorrectnessProof),
        )
        val attachment = Attachment.fromData(offer.toJson().toByteArray(), OfferCredentialMessage.INDY_CREDENTIAL_OFFER_ATTACHMENT_ID)
        val credentialPreview = CredentialPreview(options.attributes)

        val message = OfferCredentialMessage(
            options.comment,
            credentialPreview,
            listOf(attachment),
        )
        message.id = credentialRecord.threadId

        agent.didCommMessageRepository.saveAgentMessage(DidCommMessageRole.Sender, message, credentialRecord.id)

        credentialRecord.credentialAttributes = options.attributes
        credentialExchangeRepository.save(credentialRecord)
        agent.eventBus.publish(AgentEvents.CredentialEvent(credentialRecord.copy()))

        return Pair(message, credentialRecord)
    }

    /**
     * Process a received ``OfferCredentialMessage``. This will not accept the credential offer
     * or send a credential request. It will only create a new credential record with
     * the information from the credential offer message. Use ``createRequest(options:)``
     * after calling this method to create a credential request.
     *
     * @param messageContext message context containing the offer message.
     * @return credential record associated with the credential offer message.
     */
    suspend fun processOffer(messageContext: InboundMessageContext): CredentialExchangeRecord {
        val offerMessage = MessageSerializer.decodeFromString(messageContext.plaintextMessage) as OfferCredentialMessage

        require(offerMessage.getOfferAttachmentById(OfferCredentialMessage.INDY_CREDENTIAL_OFFER_ATTACHMENT_ID) != null) {
            "Indy attachment with id ${OfferCredentialMessage.INDY_CREDENTIAL_OFFER_ATTACHMENT_ID} not found in offer message"
        }

        var credentialRecord = credentialExchangeRepository.findByThreadAndConnectionId(offerMessage.threadId, messageContext.connection?.id)
        if (credentialRecord != null) {
            agent.didCommMessageRepository.saveAgentMessage(DidCommMessageRole.Receiver, offerMessage, credentialRecord.id)
            updateState(credentialRecord, CredentialState.OfferReceived)
        } else {
            val connection = messageContext.assertReadyConnection()
            credentialRecord = CredentialExchangeRecord(
                connectionId = connection.id,
                threadId = offerMessage.id,
                state = CredentialState.OfferReceived,
                protocolVersion = "v1",
            )

            agent.didCommMessageRepository.saveAgentMessage(DidCommMessageRole.Receiver, offerMessage, credentialRecord.id)
            credentialExchangeRepository.save(credentialRecord)
            agent.eventBus.publish(AgentEvents.CredentialEvent(credentialRecord.copy()))
        }

        return credentialRecord
    }

    /**
     * Create a ``RequestCredentialMessage`` as response to a received credential offer.
     *
     * @param options options for the request.
     * @return request message.
     */
    suspend fun createRequest(options: AcceptOfferOptions): RequestCredentialMessage {
        val credentialRecord = credentialExchangeRepository.getById(options.credentialRecordId)
        credentialRecord.assertProtocolVersion("v1")
        credentialRecord.assertState(CredentialState.OfferReceived)

        val offerMessageJson = agent.didCommMessageRepository.getAgentMessage(credentialRecord.id, OfferCredentialMessage.type)
        val offerMessage = MessageSerializer.decodeFromString(offerMessageJson) as OfferCredentialMessage
        val offerAttachment = offerMessage.getOfferAttachmentById(OfferCredentialMessage.INDY_CREDENTIAL_OFFER_ATTACHMENT_ID)
        checkNotNull(offerAttachment) {
            "Indy attachment with id ${OfferCredentialMessage.INDY_CREDENTIAL_OFFER_ATTACHMENT_ID} not found in offer message"
        }

        val holderDid = options.holderDid ?: getHolderDid(credentialRecord)

        val credentialOfferJson = offerMessage.getCredentialOffer()
        val credentialOffer = CredentialOffer(credentialOfferJson)
        val credentialDefinition = ledgerService.getCredentialDefinition(credentialOffer.credDefId())

        val linkSecret = agent.anoncredsService.getLinkSecret(agent.wallet.linkSecretId!!)
        val credReqTuple = Prover().createCredentialRequest(
            null,
            holderDid,
            CredentialDefinition(credentialDefinition),
            linkSecret,
            agent.wallet.linkSecretId!!,
            credentialOffer,
        )

        credentialRecord.indyRequestMetadata = credReqTuple.metadata.toJson()
        credentialRecord.credentialDefinitionId = credentialOffer.credDefId()

        val attachment = Attachment.fromData(
            credReqTuple.request.toJson().toByteArray(),
            RequestCredentialMessage.INDY_CREDENTIAL_REQUEST_ATTACHMENT_ID,
        )
        val requestMessage = RequestCredentialMessage(
            options.comment,
            listOf(attachment),
        )
        requestMessage.thread = ThreadDecorator(credentialRecord.threadId)

        credentialRecord.credentialAttributes = offerMessage.credentialPreview.attributes
        credentialRecord.autoAcceptCredential = options.autoAcceptCredential ?: credentialRecord.autoAcceptCredential

        agent.didCommMessageRepository.saveAgentMessage(DidCommMessageRole.Sender, requestMessage, credentialRecord.id)
        updateState(credentialRecord, CredentialState.RequestSent)

        return requestMessage
    }

    /**
     * Process a received ``RequestCredentialMessage``. This will not accept the credential request
     * or send a credential. It will only update the existing credential record with
     * the information from the credential request message. Use ``createCredential(options:)``
     * after calling this method to create a credential.
     *
     * @param messageContext message context containing the request message.
     * @return credential record associated with the credential request message.
     */
    suspend fun processRequest(messageContext: InboundMessageContext): CredentialExchangeRecord {
        val requestMessage = MessageSerializer.decodeFromString(messageContext.plaintextMessage) as RequestCredentialMessage

        require(requestMessage.getRequestAttachmentById(RequestCredentialMessage.INDY_CREDENTIAL_REQUEST_ATTACHMENT_ID) != null) {
            "Indy attachment with id ${RequestCredentialMessage.INDY_CREDENTIAL_REQUEST_ATTACHMENT_ID} not found in request message"
        }

        var credentialRecord = credentialExchangeRepository.getByThreadAndConnectionId(
            requestMessage.threadId,
            null,
        )

        // The credential offer may have been a connectionless-offer.
        val connection = messageContext.assertReadyConnection()
        credentialRecord.connectionId = connection.id

        agent.didCommMessageRepository.saveAgentMessage(DidCommMessageRole.Receiver, requestMessage, credentialRecord.id)
        updateState(credentialRecord, CredentialState.RequestReceived)

        return credentialRecord
    }

    /**
     * Create a ``IssueCredentialMessage`` as response to a received credential request.
     *
     * @param options options for the credential issueance.
     * @return credential message.
     */
    suspend fun createCredential(options: AcceptRequestOptions): IssueCredentialMessage {
        logger.debug("Creating credential...")
        var credentialRecord = credentialExchangeRepository.getById(options.credentialRecordId)
        credentialRecord.assertProtocolVersion("v1")
        credentialRecord.assertState(CredentialState.RequestReceived)

        val offerMessageJson = agent.didCommMessageRepository.getAgentMessage(credentialRecord.id, OfferCredentialMessage.type)
        val offerMessage = MessageSerializer.decodeFromString(offerMessageJson) as OfferCredentialMessage
        val requestMessageJson = agent.didCommMessageRepository.getAgentMessage(credentialRecord.id, RequestCredentialMessage.type)
        val requestMessage = MessageSerializer.decodeFromString(requestMessageJson) as RequestCredentialMessage

        val offerAttachment = offerMessage.getOfferAttachmentById(OfferCredentialMessage.INDY_CREDENTIAL_OFFER_ATTACHMENT_ID)
        val requestAttachment = requestMessage.getRequestAttachmentById(RequestCredentialMessage.INDY_CREDENTIAL_REQUEST_ATTACHMENT_ID)
        check(offerAttachment != null && requestAttachment != null) {
            "Missing data payload in offer or request attachment in credential Record ${credentialRecord.id}"
        }

        val offer = CredentialOffer(offerAttachment.getDataAsString())
        val request = CredentialRequest(requestAttachment.getDataAsString())
        val credDefId = offer.credDefId()
        val credentialDefinitionRecord = agent.credentialDefinitionRepository.getByCredDefId(credDefId)

        var revocationConfig: CredentialRevocationConfig? = null
        val revocationRecord = agent.revocationRegistryRepository.findByCredDefId(credDefId)
        if (revocationRecord != null) {
            val registryIndex = agent.revocationRegistryRepository.incrementRegistryIndex(credDefId)
            logger.debug("Revocation registry index: $registryIndex")
            revocationConfig = CredentialRevocationConfig(
                regDef = RevocationRegistryDefinition(revocationRecord.revocRegDef),
                regDefPrivate = RevocationRegistryDefinitionPrivate(revocationRecord.revocRegPrivate),
                statusList = RevocationStatusList(revocationRecord.revocStatusList),
                registryIndex = registryIndex.toUInt(),
            )
        }

        val credential = Issuer().createCredential(
            CredentialDefinition(credentialDefinitionRecord.credDef),
            CredentialDefinitionPrivate(credentialDefinitionRecord.credDefPriv),
            offer,
            request,
            credentialRecord.getCredentialInfo()!!.claims,
            null,
            revocationConfig,
        )

        val attachment = Attachment.fromData(
            credential.toJson().toByteArray(),
            IssueCredentialMessage.INDY_CREDENTIAL_ATTACHMENT_ID,
        )
        val issueMessage = IssueCredentialMessage(
            options.comment,
            listOf(attachment),
        )
        issueMessage.thread = ThreadDecorator(credentialRecord.threadId)

        agent.didCommMessageRepository.saveAgentMessage(DidCommMessageRole.Sender, issueMessage, credentialRecord.id)
        credentialRecord.autoAcceptCredential = options.autoAcceptCredential ?: credentialRecord.autoAcceptCredential
        updateState(credentialRecord, CredentialState.CredentialIssued)

        return issueMessage
    }

    /**
     * Process a received ``IssueCredentialMessage``. This will store the credential, but not accept it yet.
     * Use ``createAck(options:)`` after calling this method to accept the credential and create an ack message.
     *
     * @param messageContext message context containing the credential message.
     * @return credential record associated with the credential message.
     */
    suspend fun processCredential(messageContext: InboundMessageContext): CredentialExchangeRecord {
        val issueMessage = MessageSerializer.decodeFromString(messageContext.plaintextMessage) as IssueCredentialMessage

        val issueAttachment = issueMessage.getCredentialAttachmentById(IssueCredentialMessage.INDY_CREDENTIAL_ATTACHMENT_ID)
        check(issueAttachment != null) {
            "Indy attachment with id ${IssueCredentialMessage.INDY_CREDENTIAL_ATTACHMENT_ID} not found in issue message"
        }

        var credentialRecord = credentialExchangeRepository.getByThreadAndConnectionId(issueMessage.threadId, messageContext.connection?.id)
        val credential = Credential(issueAttachment.getDataAsString())
        logger.debug("Storing credential: ${credential.values()}")
        val (schemaJson, _) = ledgerService.getSchema(credential.schemaId())
        val schema = Schema(schemaJson)
        val credentialDefinition = CredentialDefinition(ledgerService.getCredentialDefinition(credential.credDefId()))
        val revocationRegistryJson = credential.revRegId()?.let { ledgerService.getRevocationRegistryDefinition(it) }
        val revocationRegistry = revocationRegistryJson?.let { RevocationRegistryDefinition(it) }
        if (revocationRegistry != null) {
            GlobalScope.launch {
                agent.revocationService.downloadTails(revocationRegistry)
            }
        }

        val linkSecret = agent.anoncredsService.getLinkSecret(agent.wallet.linkSecretId!!)
        val processedCredential = Prover().processCredential(
            credential,
            CredentialRequestMetadata(credentialRecord.indyRequestMetadata!!),
            linkSecret,
            credentialDefinition,
            revocationRegistry,
        )

        val credentialId = UUID.randomUUID().toString()
        agent.credentialRepository.save(
            CredentialRecord(
                credentialId = credentialId,
                credentialRevocationId = processedCredential.revRegIndex()?.toString(),
                revocationRegistryId = processedCredential.revRegId(),
                linkSecretId = agent.wallet.linkSecretId!!,
                credentialObject = processedCredential,
                schemaId = processedCredential.schemaId(),
                schemaName = schema.name(),
                schemaVersion = schema.version(),
                schemaIssuerId = schema.issuerId(),
                issuerId = credentialDefinition.issuerId(),
                credentialDefinitionId = processedCredential.credDefId(),
            ),
        )

        credentialRecord.credentials.add(CredentialRecordBinding("indy", credentialId))
        agent.didCommMessageRepository.saveAgentMessage(DidCommMessageRole.Receiver, issueMessage, credentialRecord.id)
        updateState(credentialRecord, CredentialState.CredentialReceived)

        return credentialRecord
    }

    /**
     * Create an ``CredentialAckMessage`` as response to a received credential.
     *
     * @param options options for the acknowledgement message.
     * @return credential acknowledgement message.
     */
    suspend fun createAck(options: AcceptCredentialOptions): CredentialAckMessage {
        var credentialRecord = credentialExchangeRepository.getById(options.credentialRecordId)
        credentialRecord.assertProtocolVersion("v1")
        credentialRecord.assertState(CredentialState.CredentialReceived)

        updateState(credentialRecord, CredentialState.Done)

        return CredentialAckMessage(credentialRecord.threadId, AckStatus.OK)
    }

    /**
     * Create an ``CredentialProblemReportMessage`` as response to a received offer.
     *
     * @param options options for the problem report message.
     * @return credential problem report message.
     */
    suspend fun createOfferDeclinedProblemReport(options: AcceptOfferOptions): CredentialProblemReportMessage {
        var credentialRecord = credentialExchangeRepository.getById(options.credentialRecordId)
        credentialRecord.assertProtocolVersion("v1")
        credentialRecord.assertState(CredentialState.OfferReceived)

        updateState(credentialRecord, CredentialState.Declined)

        return CredentialProblemReportMessage(credentialRecord.threadId)
    }

    /**
     * Process a received ``CredentialAckMessage``.
     *
     * @param messageContext message context containing the credential acknowledgement message.
     * @return credential record associated with the credential acknowledgement message.
     */
    suspend fun processAck(messageContext: InboundMessageContext): CredentialExchangeRecord {
        val ackMessage = MessageSerializer.decodeFromString(messageContext.plaintextMessage) as CredentialAckMessage

        var credentialRecord = credentialExchangeRepository.getByThreadAndConnectionId(ackMessage.threadId, messageContext.connection?.id)
        updateState(credentialRecord, CredentialState.Done)

        return credentialRecord
    }

    private suspend fun getHolderDid(credentialRecord: CredentialExchangeRecord): String {
        val connection = agent.connectionRepository.getById(credentialRecord.connectionId)
        return connection.did
    }

    suspend fun updateState(credentialRecord: CredentialExchangeRecord, newState: CredentialState) {
        credentialRecord.state = newState
        credentialExchangeRepository.update(credentialRecord)
        agent.eventBus.publish(AgentEvents.CredentialEvent(credentialRecord.copy()))
    }
}
