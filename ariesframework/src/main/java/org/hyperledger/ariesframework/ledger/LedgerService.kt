package org.hyperledger.ariesframework.ledger

import kotlinx.coroutines.future.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.toJsonString
import org.hyperledger.indy.sdk.anoncreds.Anoncreds
import org.hyperledger.indy.sdk.ledger.Ledger
import org.hyperledger.indy.sdk.pool.Pool
import org.slf4j.LoggerFactory
import java.io.File

class SchemaTemplate(val name: String, val version: String, val attributes: List<String>)
class CredentialDefinitionTemplate(
    val schema: String,
    val tag: String,
    val supportRevocation: Boolean,
    val signatureType: String = "CL",
)

@Serializable
data class IndyResponse(val op: String, val reason: String? = null)

class LedgerService(val agent: Agent) {
    private val logger = LoggerFactory.getLogger(LedgerService::class.java)
    private var indyPool: Pool? = null

    suspend fun initialize() {
        logger.info("Initializing Pool")
        if (indyPool != null) {
            logger.warn("Pool already initialized.")
            close()
        }

        Pool.setProtocolVersion(2)
        val poolConfig = "{\"genesis_txn\": \"${agent.agentConfig.genesisPath}\"}"
        if (!poolExists()) {
            try {
                Pool.createPoolLedgerConfig(agent.agentConfig.poolName, poolConfig)
            } catch (e: Exception) {
                logger.error("Cannot create pool: ${e.message}")
                throw Exception("Pool creation failed. config=$poolConfig")
            }
        }

        try {
            indyPool = Pool.openPoolLedger(agent.agentConfig.poolName, poolConfig).await()
        } catch (e: Exception) {
            logger.error("Cannot open pool: ${e.message}")
            throw Exception("Pool opening failed. config=$poolConfig")
        }
    }

    fun poolExists(): Boolean {
        val file = File(agent.context.filesDir.absolutePath, ".indy_client/pool/${agent.agentConfig.poolName}")
        return file.exists()
    }

    suspend fun registerSchema(did: String, schemaTemplate: SchemaTemplate): String {
        val schema = Anoncreds.issuerCreateSchema(
            did,
            schemaTemplate.name,
            schemaTemplate.version,
            schemaTemplate.attributes.toJsonString(),
        ).await()

        val request = Ledger.buildSchemaRequest(did, schema.schemaJson).await()
        submitWriteRequest(request, did)

        return schema.schemaId
    }

    suspend fun getSchema(schemaId: String): String {
        val request = Ledger.buildGetSchemaRequest(null, schemaId).await()
        val response = submitReadRequest(request!!)
        val schema = Ledger.parseGetSchemaResponse(response).await()
        return schema.objectJson
    }

    suspend fun registerCredentialDefinition(did: String, credentialDefinitionTemplate: CredentialDefinitionTemplate): String {
        logger.debug("Registering Credential Definition...This may hang often.")
        val credDef = Anoncreds.issuerCreateAndStoreCredentialDef(
            agent.wallet.indyWallet,
            did,
            credentialDefinitionTemplate.schema,
            credentialDefinitionTemplate.tag,
            credentialDefinitionTemplate.signatureType,
            "{\"support_revocation\": ${credentialDefinitionTemplate.supportRevocation}}",
        ).await()
        logger.debug("Credential Definition created: ${credDef.credDefJson}")

        val request = Ledger.buildCredDefRequest(did, credDef.credDefJson).await()
        submitWriteRequest(request, did)

        return credDef.credDefId
    }

    suspend fun getCredentialDefinition(id: String): String {
        logger.debug("Get CredentialDefinition with id: $id")
        val request = Ledger.buildGetCredDefRequest(null, id).await()
        val response = submitReadRequest(request)
        val credDef = Ledger.parseGetCredDefResponse(response).await()
        return credDef.objectJson
    }

    suspend fun getRevocationRegistryDefinition(id: String): String {
        logger.debug("Get RevocationRegistryDefinition with id: $id")
        val request = Ledger.buildGetRevocRegDefRequest(null, id).await()
        val response = submitReadRequest(request!!)
        val revocationRegistryDefinition = Ledger.parseGetRevocRegDefResponse(response).await()
        return revocationRegistryDefinition.objectJson
    }

    suspend fun getRevocationRegistryDelta(
        id: String,
        to: Int = (System.currentTimeMillis() / 1000L) as Int,
        from: Int = 0,
    ): Pair<String, Int> {
        logger.debug("Get RevocationRegistryDelta with id: $id")
        val request = Ledger.buildGetRevocRegDeltaRequest(null, id, from as Long, to as Long).await()
        val response = submitReadRequest(request!!)
        val revocationRegistryDelta = Ledger.parseGetRevocRegDeltaResponse(response).await()
        return Pair(revocationRegistryDelta.objectJson, revocationRegistryDelta.timestamp as Int)
    }

    suspend fun getRevocationRegistry(id: String, timestamp: Int): Pair<String, Int> {
        logger.debug("Get RevocationRegistry with id: $id, timestamp: $timestamp")
        val request = Ledger.buildGetRevocRegRequest(null, id, timestamp as Long).await()
        val response = submitReadRequest(request!!)
        val revocationRegistry = Ledger.parseGetRevocRegResponse(response).await()
        return Pair(revocationRegistry.objectJson, revocationRegistry.timestamp as Int)
    }

    private fun validateResponse(response: String) {
        val indyResponse = Json { ignoreUnknownKeys = true }.decodeFromString<IndyResponse>(response)
        if (indyResponse.op != "REPLY") {
            throw Exception("Submit request failed: ${indyResponse.reason ?: "Unknown error"}")
        }
    }

    suspend fun submitWriteRequest(request: String, did: String) {
        if (indyPool == null) {
            throw Exception("Pool is not initialized")
        }

        val response = Ledger.signAndSubmitRequest(indyPool!!, agent.wallet.indyWallet, did, request).await()
        validateResponse(response)
    }

    suspend fun submitReadRequest(request: String): String {
        if (indyPool == null) {
            throw Exception("Pool is not initialized")
        }

        val response = Ledger.submitRequest(indyPool!!, request).await()
        validateResponse(response)
        return response
    }

    suspend fun close() {
        if (indyPool != null) {
            indyPool!!.closePoolLedger().await()
            indyPool = null
        }
    }

    suspend fun delete() {
        if (indyPool != null) {
            close()
        }

        if (poolExists()) {
            Pool.deletePoolLedgerConfig(agent.agentConfig.poolName).await()
        }
    }
}
