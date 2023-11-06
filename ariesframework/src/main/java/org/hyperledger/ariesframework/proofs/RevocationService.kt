package org.hyperledger.ariesframework.proofs

import kotlinx.coroutines.future.await
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.proofs.models.IndyCredentialInfo
import org.hyperledger.ariesframework.proofs.models.PartialProof
import org.hyperledger.ariesframework.proofs.models.ProofRequest
import org.hyperledger.ariesframework.proofs.models.RequestedCredentials
import org.hyperledger.ariesframework.proofs.models.RevocationInterval
import org.hyperledger.ariesframework.proofs.models.RevocationRegistryDelta
import org.hyperledger.ariesframework.toJsonString
import org.hyperledger.ariesframework.util.concurrentForEach
import org.hyperledger.indy.sdk.anoncreds.Anoncreds
import org.hyperledger.indy.sdk.blob_storage.BlobStorageReader
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
        val revoked = revocationRegistryDelta.value.revoked?.contains(credentialRevocationIdInt) ?: false
        return Pair(revoked, deltaTimestamp)
    }

    suspend fun createRevocationState(proofRequestJson: String, requestedCredentials: RequestedCredentials): String {
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

                val revocationRegistryDefinition = agent.ledgerService.getRevocationRegistryDefinition(revocationRegistryId)
                val (revocationRegistryDelta, deltaTimestamp) = agent.ledgerService.getRevocationRegistryDelta(
                    revocationRegistryId,
                    requestRevocationInterval.to!!,
                    0,
                )
                val tailsReader = downloadTails(revocationRegistryDefinition)

                val revocationStateJson = Anoncreds.createRevocationState(
                    tailsReader.blobStorageReaderHandle,
                    revocationRegistryDefinition,
                    revocationRegistryDelta,
                    deltaTimestamp.toLong(),
                    credentialRevocationId,
                ).await()
                val revocationState = Json.decodeFromString<JsonObject>(revocationStateJson)

                if (revocationStates[revocationRegistryId] == null) {
                    revocationStates[revocationRegistryId] = mutableMapOf()
                }
                revocationStates[revocationRegistryId]!![deltaTimestamp.toString()] = revocationState
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

    fun parseRevocationRegistryDefinition(revocationRegistryDefinitionJson: String): Pair<String, String> {
        val revocationRegistryDefinition = Json.decodeFromString<JsonObject>(revocationRegistryDefinitionJson)
        val value = revocationRegistryDefinition["value"]?.jsonObject
        val tailsLocation = value?.get("tailsLocation")?.jsonPrimitive?.content
        val tailsHash = value?.get("tailsHash")?.jsonPrimitive?.content
        if (tailsLocation == null || tailsHash == null) {
            throw Exception("Could not parse tailsLocation and tailsHash from revocation registry definition")
        }
        return Pair(tailsLocation, tailsHash)
    }

    suspend fun downloadTails(revocationRegistryDefinition: String): BlobStorageReader {
        val (tailsLocation, tailsHash) = parseRevocationRegistryDefinition(revocationRegistryDefinition)
        val tailsFolder = File(agent.context.filesDir.absolutePath, "tails")
        if (!tailsFolder.exists()) {
            tailsFolder.mkdir()
        }

        val tailsFile = File(tailsFolder, tailsHash)
        if (!tailsFile.exists()) {
            val url = URL(tailsLocation)
            val tailsData = url.readBytes()
            tailsFile.writeBytes(tailsData)
        }

        return createTailsReader(tailsFile.absolutePath)
    }

    suspend fun createTailsReader(filePath: String): BlobStorageReader {
        val dirname = filePath.split("/").dropLast(1).joinToString("/")
        val tailsReaderConfig = mapOf("base_dir" to dirname).toJsonString()
        return BlobStorageReader.openReader("default", tailsReaderConfig).await()
    }
}
