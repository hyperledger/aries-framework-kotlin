package org.hyperledger.ariesframework.ledger

import anoncreds_uniffi.CredentialDefinition
import anoncreds_uniffi.Issuer
import anoncreds_uniffi.RevocationRegistryDefinition
import anoncreds_uniffi.RevocationRegistryDefinitionPrivate
import anoncreds_uniffi.Schema
import indy_vdr_uniffi.Ledger
import indy_vdr_uniffi.Pool
import indy_vdr_uniffi.Request
import indy_vdr_uniffi.openPool
import indy_vdr_uniffi.setProtocolVersion
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.anoncreds.storage.CredentialDefinitionRecord
import org.hyperledger.ariesframework.anoncreds.storage.RevocationRegistryRecord
import org.hyperledger.ariesframework.proofs.models.RevocationRegistryDelta
import org.hyperledger.ariesframework.proofs.models.RevocationStatusList
import org.hyperledger.ariesframework.wallet.DidInfo
import org.slf4j.LoggerFactory

class SchemaTemplate(val name: String, val version: String, val attributes: List<String>)
class CredentialDefinitionTemplate(
    val schema: String,
    val tag: String,
    val supportRevocation: Boolean,
    val seqNo: Int,
)
class RevocationRegistryDefinitionTemplate(
    val credDefId: String,
    val tag: String,
    val maxCredNum: Int,
    val tailsDirPath: String? = null,
)

class LedgerService(val agent: Agent) {
    private val logger = LoggerFactory.getLogger(LedgerService::class.java)
    private var pool: Pool? = null
    private val ledger = Ledger()
    private val issuer = Issuer()
    private val jsonIgnoreUnknown = Json { ignoreUnknownKeys = true }

    suspend fun initialize() {
        logger.info("Initializing Pool")
        if (pool != null) {
            logger.warn("Pool already initialized.")
            return
        }

        setProtocolVersion(2)
        try {
            pool = openPool(agent.agentConfig.genesisPath, null, null)
        } catch (e: Exception) {
            throw Exception("Pool opening failed: ${e.message}")
        }

        GlobalScope.launch {
            pool?.refresh()
            val status = pool?.getStatus()
            logger.debug("Pool status after refresh: $status")
        }
    }

    suspend fun registerSchema(did: DidInfo, schemaTemplate: SchemaTemplate): String {
        val schema = issuer.createSchema(
            schemaTemplate.name,
            schemaTemplate.version,
            did.did,
            schemaTemplate.attributes,
        )
        val indySchema = mapOf(
            "name" to JsonPrimitive(schemaTemplate.name),
            "version" to JsonPrimitive(schemaTemplate.version),
            "attrNames" to JsonArray(schemaTemplate.attributes.map { JsonPrimitive(it) }),
            "ver" to JsonPrimitive("1.0"),
            "id" to JsonPrimitive(schema.schemaId()),
        )
        val indySchemaJson = Json.encodeToString(indySchema)

        val request = ledger.buildSchemaRequest(did.did, indySchemaJson)
        submitWriteRequest(request, did)

        return schema.schemaId()
    }

    suspend fun getSchema(schemaId: String): Pair<String, Int> {
        logger.debug("Get Schema with id: $schemaId")
        val request = ledger.buildGetSchemaRequest(null, schemaId)
        val res = submitReadRequest(request)
        val response = jsonIgnoreUnknown.decodeFromString<SchemaResponse>(res)
        val seqNo = response.result.seqNo
            ?: throw Exception("Invalid schema response: $res")
        val attrNames = response.result.data.attr_names
            ?: throw Exception("Invalid schema response: $res")

        val issuer = schemaId.split(":")[0]
        val schema = mapOf(
            "name" to JsonPrimitive(response.result.data.name),
            "version" to JsonPrimitive(response.result.data.version),
            "issuerId" to JsonPrimitive(issuer),
            "attrNames" to JsonArray(attrNames.map { JsonPrimitive(it) }),
        )
        val schemaJson = Json.encodeToString(schema)

        return Pair(schemaJson, seqNo)
    }

    suspend fun registerCredentialDefinition(
        did: DidInfo,
        credentialDefinitionTemplate: CredentialDefinitionTemplate,
    ): String {
        val schema = Schema(credentialDefinitionTemplate.schema)
        val credDefTuple = issuer.createCredentialDefinition(
            schema.schemaId(),
            schema,
            credentialDefinitionTemplate.tag,
            did.did,
            credentialDefinitionTemplate.supportRevocation,
        )

        // Indy requires seqNo as schemaId for cred def registration.
        val credDefId = "${did.did}:3:CL:${credentialDefinitionTemplate.seqNo}:${credentialDefinitionTemplate.tag}"
        val indyCredDef = Json.decodeFromString<JsonObject>(credDefTuple.credDef.toJson())
        val credDef = JsonObject(
            indyCredDef.toMutableMap().apply {
                this["id"] = JsonPrimitive(credDefId)
                this["ver"] = JsonPrimitive("1.0")
                this["schemaId"] = JsonPrimitive(credentialDefinitionTemplate.seqNo.toString())
            },
        )
        val credDefJson = Json.encodeToString(credDef)
        val request = ledger.buildCredDefRequest(did.did, credDefJson)
        submitWriteRequest(request, did)

        val record = CredentialDefinitionRecord(
            schemaId = schema.schemaId(),
            credDefId = credDefId,
            credDef = credDefTuple.credDef.toJson(),
            credDefPriv = credDefTuple.credDefPriv.toJson(),
            keyCorrectnessProof = credDefTuple.keyCorrectnessProof.toJson(),
        )
        agent.credentialDefinitionRepository.save(record)

        return credDefId
    }

    suspend fun getCredentialDefinition(id: String): String {
        logger.debug("Get CredentialDefinition with id: $id")
        val request = ledger.buildGetCredDefRequest(null, id)
        val response = submitReadRequest(request)
        val json = Json.decodeFromString<JsonObject>(response)
        val result = json.get("result") as JsonObject?
        val data = result?.get("data")
        val tag = result?.get("tag")
        val type = result?.get("signature_type")
        val ref = result?.get("ref")?.jsonPrimitive?.int
        if (tag == null || type == null || ref == null) {
            throw Exception("Invalid cred def response")
        }

        val issuer = id.split(":")[0]
        val credDef = mapOf(
            "issuerId" to JsonPrimitive(issuer),
            "schemaId" to JsonPrimitive(ref.toString()),
            "type" to type,
            "tag" to tag,
            "value" to data,
        )

        return Json.encodeToString(credDef)
    }

    suspend fun registerRevocationRegistryDefinition(
        did: DidInfo,
        revRegDefTemplate: RevocationRegistryDefinitionTemplate,
    ): String {
        logger.debug("Registering RevocationRegistryDefinition")
        val credentialDefinitionRecord = agent.credentialDefinitionRepository.getByCredDefId(revRegDefTemplate.credDefId)
        val credDef = CredentialDefinition(credentialDefinitionRecord.credDef)
        val revRegDefTuple = issuer.createRevocationRegistryDef(
            credDef,
            revRegDefTemplate.credDefId,
            revRegDefTemplate.tag,
            revRegDefTemplate.maxCredNum.toUInt(),
            revRegDefTemplate.tailsDirPath,
        )
        val revRegId = revRegDefTuple.revRegDef.revRegId()
        val revocationStatusList = issuer.createRevocationStatusList(
            credDef,
            revRegId,
            revRegDefTuple.revRegDef,
            revRegDefTuple.revRegDefPriv,
            (System.currentTimeMillis() / 1000L).toULong(),
            true,
        )

        val indyRegDef = Json.decodeFromString<JsonObject>(revRegDefTuple.revRegDef.toJson())
        val value = indyRegDef["value"] as JsonObject?
            ?: throw Exception("Invalid RevocationRegistryDefinition. value is missing.")
        val regDef = JsonObject(
            indyRegDef.toMutableMap().apply {
                this["id"] = JsonPrimitive(revRegId)
                this["ver"] = JsonPrimitive("1.0")
                this["value"] = JsonObject(
                    value.toMutableMap().apply {
                        this["issuanceType"] = JsonPrimitive("ISSUANCE_BY_DEFAULT")
                    },
                )
            },
        )
        val regDefJson = Json.encodeToString(regDef)
        val request = ledger.buildRevocRegDefRequest(did.did, regDefJson)
        submitWriteRequest(request, did)

        val statusList = jsonIgnoreUnknown.decodeFromString<RevocationStatusList>(revocationStatusList.toJson())
        val regDelta = RevocationRegistryDelta(
            accum = statusList.currentAccumulator,
        )
        val entryRequest = ledger.buildRevocRegEntryRequest(did.did, revRegId, regDelta.toVersionedJson())
        submitWriteRequest(entryRequest, did)

        val record = RevocationRegistryRecord(
            credDefId = revRegDefTemplate.credDefId,
            revocRegId = revRegId,
            revocRegDef = revRegDefTuple.revRegDef.toJson(),
            revocRegPrivate = revRegDefTuple.revRegDefPriv.toJson(),
            revocStatusList = revocationStatusList.toJson(),
        )
        agent.revocationRegistryRepository.save(record)

        return revRegId
    }

    suspend fun getRevocationRegistryDefinition(id: String): String {
        logger.debug("Get RevocationRegistryDefinition with id: $id")
        val request = ledger.buildGetRevocRegDefRequest(null, id)
        val response = submitReadRequest(request)
        val json = Json.decodeFromString<JsonObject>(response)
        val result = json["result"] as JsonObject?
            ?: throw Exception("Invalid rev reg def response")
        val indyData = result["data"] as JsonObject?
            ?: throw Exception("Invalid rev reg def response")
        val issuerId = id.split(":")[0]
        val data = JsonObject(
            indyData.toMutableMap().apply {
                this["issuerId"] = JsonPrimitive(issuerId)
            },
        )

        return Json.encodeToString(data)
    }

    suspend fun getRevocationRegistryDelta(
        id: String,
        to: Int = (System.currentTimeMillis() / 1000L).toInt(),
        from: Int = 0,
    ): Pair<String, Int> {
        logger.debug("Get RevocationRegistryDelta with id: $id")
        val request = ledger.buildGetRevocRegDeltaRequest(null, id, from.toLong(), to.toLong())
        val res = submitReadRequest(request)
        val response = jsonIgnoreUnknown.decodeFromString<RevRegDeltaResponse>(res)
        val value = response.result.data.value
        val revocationRegistryDelta = RevocationRegistryDelta(
            prevAccum = value.accum_from?.value?.accum,
            accum = value.accum_to.value.accum,
            issued = value.issued,
            revoked = value.revoked,
        )
        val deltaTimestamp = value.accum_to.txnTime

        return Pair(revocationRegistryDelta.toJsonString(), deltaTimestamp)
    }

    suspend fun getRevocationRegistry(id: String, timestamp: Int): Pair<String, Int> {
        logger.debug("Get RevocationRegistry with id: $id, timestamp: $timestamp")
        val request = ledger.buildGetRevocRegRequest(null, id, timestamp.toLong())
        val response = submitReadRequest(request)
        val json = Json.decodeFromString<JsonObject>(response)
        val result = json.get("result") as JsonObject?
        val data = result?.get("data") as JsonObject?
        val value = data?.get("value") as JsonObject?
        val txnTime = result?.get("txnTime")?.jsonPrimitive?.int
        if (value == null || txnTime == null) {
            throw Exception("Invalid rev reg response: $response")
        }

        return Pair(Json.encodeToString(value), txnTime)
    }

    suspend fun revokeCredential(did: DidInfo, credDefId: String, revocationIndex: Int) {
        logger.debug("Revoking credential with index: $revocationIndex")
        val credentialDefinitionRecord = agent.credentialDefinitionRepository.getByCredDefId(credDefId)
        val revocationRecord = agent.revocationRegistryRepository.findByCredDefId(credDefId)
            ?: throw Exception("No revocation registry found for credential definition id: $credDefId")

        val currentStatusList = anoncreds_uniffi.RevocationStatusList(revocationRecord.revocStatusList)
        val revokedStatusList = issuer.updateRevocationStatusList(
            CredentialDefinition(credentialDefinitionRecord.credDef),
            (System.currentTimeMillis() / 1000L).toULong(),
            null,
            listOf(revocationIndex.toUInt()),
            RevocationRegistryDefinition(revocationRecord.revocRegDef),
            RevocationRegistryDefinitionPrivate(revocationRecord.revocRegPrivate),
            currentStatusList,
        )

        val currentList = Json.decodeFromString<RevocationStatusList>(currentStatusList.toJson())
        val revokedList = Json.decodeFromString<RevocationStatusList>(revokedStatusList.toJson())
        val regDelta = RevocationRegistryDelta(
            currentList.currentAccumulator,
            revokedList.currentAccumulator,
            null,
            listOf(revocationIndex),
        )
        logger.debug("RevocationRegistryDelta: ${regDelta.toVersionedJson()}")
        val request = ledger.buildRevocRegEntryRequest(did.did, revocationRecord.revocRegId, regDelta.toVersionedJson())
        submitWriteRequest(request, did)

        revocationRecord.revocStatusList = revokedStatusList.toJson()
        agent.revocationRegistryRepository.update(revocationRecord)
    }

    private fun validateResponse(response: String) {
        val indyResponse = jsonIgnoreUnknown.decodeFromString<IndyResponse>(response)
        if (indyResponse.op != "REPLY") {
            throw Exception("Submit request failed: ${indyResponse.reason ?: "Unknown error"}")
        }
    }

    suspend fun submitWriteRequest(request: Request, did: DidInfo) {
        if (pool == null) {
            throw Exception("Pool is not initialized")
        }

        val signKey = agent.wallet.session!!.fetchKey(did.verkey, false)
            ?: throw Exception("Key not found: ${did.verkey}")
        val signatureData = request.signatureInput().encodeToByteArray()
        val signature = signKey.loadLocalKey().signMessage(signatureData, null)
        request.setSignature(signature)

        val response = pool!!.submitRequest(request)
        validateResponse(response)
    }

    suspend fun submitReadRequest(request: Request): String {
        if (pool == null) {
            throw Exception("Pool is not initialized")
        }

        val response = pool!!.submitRequest(request)
        validateResponse(response)
        return response
    }

    fun close() {
        logger.warn("Do not call close on LedgerService. It will be auto closed")
    }
}
