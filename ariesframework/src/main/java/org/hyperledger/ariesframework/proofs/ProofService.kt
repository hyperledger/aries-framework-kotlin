package org.hyperledger.ariesframework.proofs

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.hyperledger.ariesframework.AckStatus
import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.AgentEvents
import org.hyperledger.ariesframework.agent.MessageSerializer
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.agent.decorators.ThreadDecorator
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.hyperledger.ariesframework.proofs.messages.PresentationAckMessage
import org.hyperledger.ariesframework.proofs.messages.PresentationMessage
import org.hyperledger.ariesframework.proofs.messages.PresentationProblemReportMessage
import org.hyperledger.ariesframework.proofs.messages.RequestPresentationMessage
import org.hyperledger.ariesframework.proofs.models.AutoAcceptProof
import org.hyperledger.ariesframework.proofs.models.CredentialsForProof
import org.hyperledger.ariesframework.proofs.models.IndyCredentialInfo
import org.hyperledger.ariesframework.proofs.models.PartialProof
import org.hyperledger.ariesframework.proofs.models.ProofRequest
import org.hyperledger.ariesframework.proofs.models.ProofState
import org.hyperledger.ariesframework.proofs.models.RequestedAttribute
import org.hyperledger.ariesframework.proofs.models.RequestedCredentials
import org.hyperledger.ariesframework.proofs.models.RequestedPredicate
import org.hyperledger.ariesframework.proofs.models.RetrievedCredentials
import org.hyperledger.ariesframework.proofs.models.RevocationInterval
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord
import org.hyperledger.ariesframework.storage.DidCommMessageRole
import org.hyperledger.ariesframework.util.concurrentForEach
import org.hyperledger.ariesframework.util.concurrentMap
import org.hyperledger.indy.sdk.anoncreds.Anoncreds
import org.slf4j.LoggerFactory

class ProofService(val agent: Agent) {
    private val logger = LoggerFactory.getLogger(ProofService::class.java)

    companion object {
        /**
         * Generates 80-bit numbers that can be used as a nonce for proof request.
         *
         * @return generated number as a string.
         */
        suspend fun generateProofRequestNonce(): String {
            return Anoncreds.generateNonce().await()
        }
    }

    /**
     * Creates a new ``RequestPresentationMessage``.
     *
     * @param proofRequest the proof request template.
     * @param connectionRecord the connection for which to create the presentation request.
     * @param comment A comment to include in the presentation request.
     * @param autoAcceptProof whether to automatically accept the presentation.
     * @return the presentation request message and a new proof record for the proof exchange.
     */
    suspend fun createRequest(
        proofRequest: ProofRequest,
        connectionRecord: ConnectionRecord,
        comment: String? = null,
        autoAcceptProof: AutoAcceptProof? = null,
    ): Pair<RequestPresentationMessage, ProofExchangeRecord> {
        connectionRecord.assertReady()

        val proofRequestJson = Json.encodeToString(proofRequest)
        val attachment = Attachment.fromData(proofRequestJson.toByteArray(), RequestPresentationMessage.INDY_PROOF_REQUEST_ATTACHMENT_ID)
        val message = RequestPresentationMessage(comment, listOf(attachment))

        val proofRecord = ProofExchangeRecord(
            connectionId = connectionRecord.id,
            threadId = message.threadId,
            state = ProofState.RequestSent,
            autoAcceptProof = autoAcceptProof,
        )

        agent.didCommMessageRepository.saveAgentMessage(DidCommMessageRole.Sender, message, proofRecord.id)

        agent.proofRepository.save(proofRecord)
        agent.eventBus.publish(AgentEvents.ProofEvent(proofRecord.copy()))

        return Pair(message, proofRecord)
    }

    /**
     * Process a received ``RequestPresentationMessage``. This will not accept the presentation request
     * or send a presentation. It will only create a new, or update the existing proof record with
     * the information from the presentation request message. Use  ``createPresentation(proofRecord:requestedCredentials:comment:)``
     * after calling this method to create a presentation.
     *
     * @param messageContext the message context containing a presentation request message.
     * @return proof record associated with the presentation request message.
     */
    suspend fun processRequest(messageContext: InboundMessageContext): ProofExchangeRecord {
        val proofRequestMessage = MessageSerializer.decodeFromString(messageContext.plaintextMessage)

        val connection = messageContext.assertReadyConnection()
        val proofRecord = ProofExchangeRecord(
            connectionId = connection.id,
            threadId = proofRequestMessage.threadId,
            state = ProofState.RequestReceived,
        )

        agent.didCommMessageRepository.saveAgentMessage(DidCommMessageRole.Receiver, proofRequestMessage, proofRecord.id)

        agent.proofRepository.save(proofRecord)
        agent.eventBus.publish(AgentEvents.ProofEvent(proofRecord.copy()))

        return proofRecord
    }

    /**
     * Create a ``PresentationMessage`` as response to a received presentation request.
     *
     * @param proofRecord the proof record for which to create the presentation.
     * @param requestedCredentials the requested credentials object specifying which credentials to use for the proof.
     * @param comment a comment to include in the presentation.
     * @return the presentation message and an associated proof record.
     */
    suspend fun createPresentation(
        proofRecord: ProofExchangeRecord,
        requestedCredentials: RequestedCredentials,
        comment: String? = null,
    ): Pair<PresentationMessage, ProofExchangeRecord> {
        proofRecord.assertState(ProofState.RequestReceived)

        val proofRequestMessageJson = agent.didCommMessageRepository.getAgentMessage(proofRecord.id, RequestPresentationMessage.type)
        val proofRequestMessage = MessageSerializer.decodeFromString(proofRequestMessageJson) as RequestPresentationMessage

        val proof = createProof(proofRequestMessage.indyProofRequest(), requestedCredentials)

        val attachment = Attachment.fromData(proof, PresentationMessage.INDY_PROOF_ATTACHMENT_ID)
        val presentationMessage = PresentationMessage(comment, listOf(attachment))
        presentationMessage.thread = ThreadDecorator(proofRecord.threadId)

        agent.didCommMessageRepository.saveAgentMessage(DidCommMessageRole.Sender, presentationMessage, proofRecord.id)
        updateState(proofRecord, ProofState.PresentationSent)

        return Pair(presentationMessage, proofRecord)
    }

    /**
     * Process a received ``PresentationMessage``. This will not accept the presentation
     * or send a presentation acknowledgement. It will only update the existing proof record with
     * the information from the presentation message. Use  ``createAck(proofRecord:)``
     * after calling this method to create a presentation acknowledgement.
     *
     * @param messageContext the message context containing a presentation message.
     * @return proof record associated with the presentation message.
     */
    suspend fun processPresentation(messageContext: InboundMessageContext): ProofExchangeRecord {
        val presentationMessage = MessageSerializer.decodeFromString(messageContext.plaintextMessage) as PresentationMessage
        val connection = messageContext.assertReadyConnection()

        val proofRecord = agent.proofRepository.getByThreadAndConnectionId(presentationMessage.threadId, connection.id)
        proofRecord.assertState(ProofState.RequestSent)

        val indyProofJson = presentationMessage.indyProof()
        val requestMessageJson = agent.didCommMessageRepository.getAgentMessage(proofRecord.id, RequestPresentationMessage.type)
        val requestMessage = MessageSerializer.decodeFromString(requestMessageJson) as RequestPresentationMessage
        val indyProofRequest = requestMessage.indyProofRequest()

        proofRecord.isVerified = verifyProof(indyProofRequest, indyProofJson)

        agent.didCommMessageRepository.saveAgentMessage(DidCommMessageRole.Receiver, presentationMessage, proofRecord.id)
        updateState(proofRecord, ProofState.PresentationReceived)

        return proofRecord
    }

    /**
     * Create a ``PresentationAckMessage`` as response to a received presentation.
     *
     * @param proofRecord the proof record for which to create the presentation acknowledgement.
     * @return the presentation acknowledgement message and an associated proof record.
     */
    suspend fun createAck(proofRecord: ProofExchangeRecord): Pair<PresentationAckMessage, ProofExchangeRecord> {
        proofRecord.assertState(ProofState.PresentationReceived)

        val ackMessage = PresentationAckMessage(proofRecord.threadId, AckStatus.OK)
        updateState(proofRecord, ProofState.Done)

        return Pair(ackMessage, proofRecord)
    }

    /**
     * Create a ``PresentationProblemReport`` as response to a received presentation request.
     *
     * @param proofRecord the proof record for which to create the presentation acknowledgement.
     * @return the presentation problem report message and an associated proof record.
     */
    suspend fun createPresentationDeclinedProblemReport(proofRecord: ProofExchangeRecord): Pair<PresentationProblemReportMessage, ProofExchangeRecord> {
        proofRecord.assertState(ProofState.RequestReceived)

        val probMessage = PresentationProblemReportMessage(proofRecord.threadId)
        updateState(proofRecord, ProofState.Declined)

        return Pair(probMessage, proofRecord)
    }

    /**
     * Process a received ``PresentationAckMessage``.
     *
     * @param messageContext the message context containing a presentation acknowledgement message.
     * @return proof record associated with the presentation acknowledgement message.
     */
    suspend fun processAck(messageContext: InboundMessageContext): ProofExchangeRecord {
        val ackMessage = MessageSerializer.decodeFromString(messageContext.plaintextMessage)
        val connection = messageContext.assertReadyConnection()

        val proofRecord = agent.proofRepository.getByThreadAndConnectionId(ackMessage.threadId, connection.id)
        proofRecord.assertState(ProofState.PresentationSent)

        updateState(proofRecord, ProofState.Done)

        return proofRecord
    }

    /**
     * Create a ``RetrievedCredentials`` object. Given input proof request,
     * use credentials in the wallet to build indy requested credentials object for proof creation.
     *
     * @param proofRequest the proof request to build the requested credentials object from.
     * @return ``RetrievedCredentials`` object.
     */
    suspend fun getRequestedCredentialsForProofRequest(proofRequest: ProofRequest): RetrievedCredentials {
        val retrievedCredentials = RetrievedCredentials()
        val credentialsForProof = getCredentialsForProofRequest(proofRequest)
        val lock = Mutex()

        proofRequest.requestedAttributes.concurrentForEach { (referent, requestedAttribute) ->
            val credentials = credentialsForProof.attrs[referent] ?: return@concurrentForEach

            val attributes = credentials.concurrentMap { credential ->
                val (revoked, deltaTimestamp) = getRevocationStatusForRequestedItem(
                    proofRequest,
                    requestedAttribute.nonRevoked,
                    credential.credentialInfo,
                )

                RequestedAttribute(credential.credentialInfo.referent, deltaTimestamp, true, credential.credentialInfo, revoked)
            }
            lock.withLock {
                retrievedCredentials.requestedAttributes[referent] = attributes
            }
        }

        proofRequest.requestedPredicates.concurrentForEach { (referent, requestedPredicate) ->
            val credentials = credentialsForProof.predicates[referent] ?: return@concurrentForEach

            val predicates = credentials.concurrentMap { credential ->
                val (revoked, deltaTimestamp) = getRevocationStatusForRequestedItem(
                    proofRequest,
                    requestedPredicate.nonRevoked,
                    credential.credentialInfo,
                )

                RequestedPredicate(credential.credentialInfo.referent, deltaTimestamp, credential.credentialInfo, revoked)
            }
            lock.withLock {
                retrievedCredentials.requestedPredicates[referent] = predicates
            }
        }

        return retrievedCredentials
    }

    /**
     * Takes a ``RetrievedCredentials`` object and auto selects credentials in a ``RequestedCredentials`` object.
     *
     * Use the return value of this method as input to ``createPresentation(proofRecord:requestedCredentials:comment:)`` to
     * automatically select credentials for presentation.
     *
     * @param retrievedCredentials the retrieved credentials to auto select from.
     * @return a ``RequestedCredentials`` object.
     */
    suspend fun autoSelectCredentialsForProofRequest(retrievedCredentials: RetrievedCredentials): RequestedCredentials {
        val requestedCredentials = RequestedCredentials()
        retrievedCredentials.requestedAttributes.keys.forEach { attributeName ->
            val attributeArray = retrievedCredentials.requestedAttributes[attributeName]!!

            if (attributeArray.isEmpty()) {
                throw Exception("Cannot find credentials for attribute '$attributeName'.")
            }
            val nonRevokedAttributes = attributeArray.filter { attr ->
                attr.revoked != true
            }
            if (nonRevokedAttributes.isEmpty()) {
                throw Exception("Cannot find non-revoked credentials for attribute '$attributeName'.")
            }
            requestedCredentials.requestedAttributes[attributeName] = attributeArray[0]
        }

        retrievedCredentials.requestedPredicates.keys.forEach { predicateName ->
            val predicateArray = retrievedCredentials.requestedPredicates[predicateName]!!

            if (predicateArray.isEmpty()) {
                throw Exception("Cannot find credentials for predicate '$predicateName'.")
            }
            val nonRevokedPredicates = predicateArray.filter { pred ->
                pred.revoked != true
            }
            if (nonRevokedPredicates.isEmpty()) {
                throw Exception("Cannot find non-revoked credentials for predicate '$predicateName'.")
            }
            requestedCredentials.requestedPredicates[predicateName] = nonRevokedPredicates[0]
        }

        return requestedCredentials
    }

    /**
     * Verify an indy proof object.
     *
     * @param proofRequest the proof request to use for proof verification.
     * @param proof the proof to verify.
     * @return true if the proof is valid, false otherwise.
     */
    suspend fun verifyProof(proofRequest: String, proof: String): Boolean {
        logger.debug("verifying proof: $proof")
        val partialProof = Json { ignoreUnknownKeys = true }.decodeFromString<PartialProof>(proof)
        val schemas = getSchemas(partialProof.identifiers.map { it.schemaId }.toSet())
        val credentialDefinitions = getCredentialDefinitions(partialProof.identifiers.map { it.credentialDefinitionId }.toSet())
        val revocationRegistryDefinitions =
            getRevocationRegistryDefinitions(partialProof.identifiers.mapNotNull { it.revocationRegistryId }.toSet())
        val revocationRegistries = agent.revocationService.getRevocationRegistries(proof = partialProof)

        logger.trace("revocationRegistryDefinitions: $revocationRegistryDefinitions")
        logger.trace("revocationRegistries: $revocationRegistries")

        return Anoncreds.verifierVerifyProof(
            proofRequest,
            proof,
            schemas,
            credentialDefinitions,
            revocationRegistryDefinitions,
            revocationRegistries,
        ).await()
    }

    suspend fun getRevocationStatusForRequestedItem(
        proofRequest: ProofRequest,
        nonRevoked: RevocationInterval?,
        credential: IndyCredentialInfo,
    ): Pair<Boolean?, Int?> {
        val requestNonRevoked = nonRevoked ?: proofRequest.nonRevoked
        val credentialRevocationId = credential.credentialRevocationId
        val revocationRegistryId = credential.revocationRegistryId
        if (requestNonRevoked == null || credentialRevocationId == null || revocationRegistryId == null) {
            return Pair(null, null)
        }

        return agent.revocationService.getRevocationStatus(credentialRevocationId, revocationRegistryId, requestNonRevoked)
    }

    suspend fun getCredentialsForProofRequest(proofRequest: ProofRequest): CredentialsForProof {
        val credentials = Anoncreds.proverGetCredentialsForProofReq(agent.wallet.indyWallet, proofRequest.toJsonString()).await()
        logger.debug("Got credentials for proof request: $credentials")
        return Json { ignoreUnknownKeys = true }.decodeFromString(credentials!!)
    }

    suspend fun createProof(proofRequest: String, requestedCredentials: RequestedCredentials): ByteArray = coroutineScope {
        logger.debug("Creating proof with requestedCredentials: ${requestedCredentials.toJsonString()}")
        val credentialObjects = mutableListOf<IndyCredentialInfo>()
        val lock = Mutex()

        requestedCredentials.requestedAttributes.concurrentForEach { (_, attribute) ->
            val credentialInfo = getCredential(attribute.credentialId)
            attribute.credentialInfo = credentialInfo
            lock.withLock {
                credentialObjects.add(credentialInfo)
            }
        }
        requestedCredentials.requestedPredicates.concurrentForEach { (_, attribute) ->
            val credentialInfo = getCredential(attribute.credentialId)
            attribute.credentialInfo = credentialInfo
            lock.withLock {
                credentialObjects.add(credentialInfo)
            }
        }

        val schemas = async { getSchemas(credentialObjects.map { it.schemaId }.toSet()) }
        val credentialDefinitions = async { getCredentialDefinitions(credentialObjects.map { it.credentialDefinitionId }.toSet()) }
        val revocationStates = async { agent.revocationService.createRevocationState(proofRequest, requestedCredentials) }

        logger.trace("schemas: ${schemas.await()}")
        logger.trace("credentialDefinitions: ${credentialDefinitions.await()}")
        logger.trace("revocationStates: ${revocationStates.await()}")

        val indyProof = Anoncreds.proverCreateProof(
            agent.wallet.indyWallet,
            proofRequest,
            requestedCredentials.toJsonString(),
            agent.wallet.masterSecretId,
            schemas.await(),
            credentialDefinitions.await(),
            revocationStates.await(),
        ).await()

        logger.trace("created proof: $indyProof")

        return@coroutineScope indyProof!!.toByteArray()
    }

    suspend fun getCredential(credentialId: String): IndyCredentialInfo {
        val credential = Anoncreds.proverGetCredential(agent.wallet.indyWallet, credentialId).await()
        return Json { ignoreUnknownKeys = true }.decodeFromString(credential!!)
    }

    suspend fun getSchemas(schemaIds: Set<String>): String {
        val schemas = mutableMapOf<String, JsonObject>()
        val lock = Mutex()

        schemaIds.concurrentForEach { schemaId ->
            val schema = agent.ledgerService.getSchema(schemaId)
            val schemaObj = Json.decodeFromString<JsonObject>(schema)
            lock.withLock {
                schemas[schemaId] = schemaObj
            }
        }

        return Json.encodeToString(schemas)
    }

    suspend fun getCredentialDefinitions(credentialDefinitionIds: Set<String>): String {
        val credentialDefinitions = mutableMapOf<String, JsonObject>()
        val lock = Mutex()

        credentialDefinitionIds.concurrentForEach { credentialDefinitionId ->
            val credentialDefinition = agent.ledgerService.getCredentialDefinition(credentialDefinitionId)
            val credentialDefinitionObj = Json.decodeFromString<JsonObject>(credentialDefinition)
            lock.withLock {
                credentialDefinitions[credentialDefinitionId] = credentialDefinitionObj
            }
        }

        return Json.encodeToString(credentialDefinitions)
    }

    suspend fun getRevocationRegistryDefinitions(revocationRegistryIds: Set<String>): String {
        val revocationRegistryDefinitions = mutableMapOf<String, JsonObject>()
        val lock = Mutex()

        revocationRegistryIds.concurrentForEach { revocationRegistryId ->
            val revocationRegistryDefinition = agent.ledgerService.getRevocationRegistryDefinition(revocationRegistryId)
            val revocationRegistryDefinitionObj = Json.decodeFromString<JsonObject>(revocationRegistryDefinition)
            lock.withLock {
                revocationRegistryDefinitions[revocationRegistryId] = revocationRegistryDefinitionObj
            }
        }

        return Json.encodeToString(revocationRegistryDefinitions)
    }

    suspend fun updateState(proofRecord: ProofExchangeRecord, newState: ProofState) {
        proofRecord.state = newState
        agent.proofRepository.update(proofRecord)
        agent.eventBus.publish(AgentEvents.ProofEvent(proofRecord.copy()))
    }
}
