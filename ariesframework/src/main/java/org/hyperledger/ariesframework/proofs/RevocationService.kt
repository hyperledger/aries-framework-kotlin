package org.hyperledger.ariesframework.proofs

import anoncreds_uniffi.Credential
import anoncreds_uniffi.CredentialRevocationState
import anoncreds_uniffi.Prover
import anoncreds_uniffi.RevocationRegistryDefinition
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.proofs.models.IndyCredentialInfo
import org.hyperledger.ariesframework.proofs.models.PartialProof
import org.hyperledger.ariesframework.proofs.models.ProofRequest
import org.hyperledger.ariesframework.proofs.models.RequestedCredentials
import org.hyperledger.ariesframework.proofs.models.RevocationInterval
import org.hyperledger.ariesframework.proofs.models.RevocationRegistryDelta
import org.hyperledger.ariesframework.proofs.models.RevocationStatusList
import org.hyperledger.ariesframework.util.concurrentForEach
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URL

private enum class ReferentType {
    Attribute,
    Predicate,
}

private data class ReferentCredential(
    val referent: String,
    val credentialInfo: IndyCredentialInfo,
    val type: ReferentType,
)

class RevocationService(val agent: Agent) {
    private val logger = LoggerFactory.getLogger(RevocationService::class.java)

    suspend fun getRevocationRegistries(proof: PartialProof): String {
        val revocationRegistries = mutableMapOf<String, MutableMap<String, JsonObject>>()

        proof.identifiers.concurrentForEach { identifier ->
            if (identifier.revocationRegistryId != null && identifier.timestamp != null) {
                if (revocationRegistries[identifier.revocationRegistryId] == null) {
                    revocationRegistries[identifier.revocationRegistryId] = mutableMapOf()
                }
                if (revocationRegistries[identifier.revocationRegistryId]!![identifier.timestamp.toString()] == null) {
                    val (revocationRegistryJson, _) = agent.ledgerService.getRevocationRegistry(
                        identifier.revocationRegistryId,
                        identifier.timestamp,
                    )
                    val revocationRegistry = Json.decodeFromString<JsonObject>(revocationRegistryJson)
                    revocationRegistries[identifier.revocationRegistryId]!![identifier.timestamp.toString()] =
                        revocationRegistry
                }
            }
        }

        return Json.encodeToString(revocationRegistries)
    }

    suspend fun getRevocationStatusLists(
        proof: PartialProof,
        revocationRegistryDefinitions: Map<String, RevocationRegistryDefinition>,
    ): List<anoncreds_uniffi.RevocationStatusList> {
        val revocationStatusLists = mutableListOf<anoncreds_uniffi.RevocationStatusList>()

        proof.identifiers.concurrentForEach { identifier ->
            if (identifier.revocationRegistryId != null && identifier.timestamp != null) {
                val revocationRegistryDefinition = revocationRegistryDefinitions[identifier.revocationRegistryId]
                    ?: throw Exception("Revocation registry definition not found for id: ${identifier.revocationRegistryId}")

                val (revocationRegistryJson, _) = agent.ledgerService.getRevocationRegistry(
                    identifier.revocationRegistryId,
                    identifier.timestamp,
                )
                val revocationRegistryDelta = Json.decodeFromString<RevocationRegistryDelta>(revocationRegistryJson)
                logger.debug("Revocation registry at time ${identifier.timestamp}: $revocationRegistryJson")
                val revocationStatusList = RevocationStatusList(
                    revocationRegistryDefinition.issuerId(),
                    revocationRegistryDelta.accum,
                    identifier.revocationRegistryId,
                    emptyList(),
                    identifier.timestamp,
                )
                revocationStatusLists.add(anoncreds_uniffi.RevocationStatusList(revocationStatusList.toJsonString()))
            }
        }

        return revocationStatusLists
    }

    suspend fun getRevocationStatus(
        credentialRevocationId: String,
        revocationRegistryId: String,
        revocationInterval: RevocationInterval,
    ): Pair<Boolean, Int> {
        val (revocationRegistryDeltaJson, deltaTimestamp) = agent.ledgerService.getRevocationRegistryDelta(
            revocationRegistryId,
            revocationInterval.to!!,
            0,
        )
        val revocationRegistryDelta = Json.decodeFromString<RevocationRegistryDelta>(revocationRegistryDeltaJson)
        val credentialRevocationIdInt = credentialRevocationId.toInt()
        val revoked = revocationRegistryDelta.revoked?.contains(credentialRevocationIdInt) ?: false
        return Pair(revoked, deltaTimestamp)
    }

    suspend fun createRevocationState(
        credential: Credential,
        timestamp: Int,
    ): CredentialRevocationState {
        val credentialRevocationId = credential.revRegIndex()
            ?: throw Exception("Credential does not have revocation information.")
        val revocationRegistryId = credential.revRegId()
            ?: throw Exception("Credential does not have revocation information.")

        val revocationRegistryDefinition = RevocationRegistryDefinition(
            agent.ledgerService.getRevocationRegistryDefinition(revocationRegistryId),
        )
        val (revocationRegistryDelta, deltaTimestamp) = agent.ledgerService.getRevocationRegistryDelta(
            revocationRegistryId,
            timestamp,
            0,
        )
        val tailsFile = downloadTails(revocationRegistryDefinition)

        return Prover().createRevocationState(
            revocationRegistryDefinition,
            anoncreds_uniffi.RevocationRegistryDelta(revocationRegistryDelta),
            deltaTimestamp.toULong(),
            credentialRevocationId,
            tailsFile.absolutePath,
        )
    }

    suspend fun createRevocationStates(proofRequestJson: String, requestedCredentials: RequestedCredentials): String {
        val revocationStates = mutableMapOf<String, MutableMap<String, JsonObject>>()
        val referentCredentials = mutableListOf<ReferentCredential>()

        requestedCredentials.requestedAttributes.forEach { (k, v) ->
            referentCredentials.add(ReferentCredential(k, v.credentialInfo!!, ReferentType.Attribute))
        }
        requestedCredentials.requestedPredicates.forEach { (k, v) ->
            referentCredentials.add(ReferentCredential(k, v.credentialInfo!!, ReferentType.Predicate))
        }

        val proofRequest = Json.decodeFromString<ProofRequest>(proofRequestJson)
        referentCredentials.concurrentForEach { credential ->
            val referentRevocationInterval = if (credential.type == ReferentType.Attribute) {
                proofRequest.requestedAttributes[credential.referent]?.nonRevoked
            } else {
                proofRequest.requestedPredicates[credential.referent]?.nonRevoked
            }
            val requestRevocationInterval = referentRevocationInterval ?: proofRequest.nonRevoked
            val credentialRevocationId = credential.credentialInfo.credentialRevocationId
            val revocationRegistryId = credential.credentialInfo.revocationRegistryId
            if (requestRevocationInterval != null && credentialRevocationId != null && revocationRegistryId != null) {
                assertRevocationInterval(requestRevocationInterval)

                val revocationRegistryDefinition = RevocationRegistryDefinition(agent.ledgerService.getRevocationRegistryDefinition(revocationRegistryId))
                val (revocationRegistryDelta, deltaTimestamp) = agent.ledgerService.getRevocationRegistryDelta(
                    revocationRegistryId,
                    requestRevocationInterval.to!!,
                    0,
                )
                val tailsReader = downloadTails(revocationRegistryDefinition)

                val revocationState = Prover().createRevocationState(
                    revocationRegistryDefinition,
                    anoncreds_uniffi.RevocationRegistryDelta(revocationRegistryDelta),
                    deltaTimestamp.toULong(),
                    credentialRevocationId.toUInt(),
                    tailsReader.absolutePath,
                )
                val revocationStateObj = Json.decodeFromString<JsonObject>(revocationState.toJson())

                if (revocationStates[revocationRegistryId] == null) {
                    revocationStates[revocationRegistryId] = mutableMapOf()
                }
                revocationStates[revocationRegistryId]!![deltaTimestamp.toString()] = revocationStateObj
            }
        }

        return Json.encodeToString(revocationStates)
    }

    private fun assertRevocationInterval(requestRevocationInterval: RevocationInterval) {
        if (requestRevocationInterval.to == null) {
            throw Exception("Presentation requests proof of non-revocation with no 'to' value specified")
        }

        if (requestRevocationInterval.from != null && requestRevocationInterval.to != requestRevocationInterval.from) {
            throw Exception(
                "Presentation requests proof of non-revocation with an interval from: '${requestRevocationInterval.from}'" +
                    " that does not match the interval to: '${requestRevocationInterval.to}', as specified in Aries RFC 0441",
            )
        }
    }

    suspend fun downloadTails(revocationRegistryDefinition: RevocationRegistryDefinition): File {
        logger.debug("Downloading tails file for revocation registry definition: ${revocationRegistryDefinition.revRegId()}")
        val tailsFolder = File(agent.context.filesDir.absolutePath, "tails")
        if (!tailsFolder.exists()) {
            tailsFolder.mkdir()
        }

        val tailsFile = File(tailsFolder, revocationRegistryDefinition.tailsHash())
        if (!tailsFile.exists()) {
            val tailsLocation = revocationRegistryDefinition.tailsLocation()
            logger.debug("Downloading tails file from: $tailsLocation")
            val url = if (tailsLocation.startsWith("http")) {
                URL(tailsLocation)
            } else {
                File(tailsLocation).toURI().toURL()
            }
            val tailsData = url.readBytes()
            tailsFile.writeBytes(tailsData)
        }

        return tailsFile
    }
}
