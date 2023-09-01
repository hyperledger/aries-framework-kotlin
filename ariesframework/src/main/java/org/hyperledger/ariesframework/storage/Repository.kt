package org.hyperledger.ariesframework.storage

import kotlinx.coroutines.future.await
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.hyperledger.ariesframework.Tags
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.connection.models.didauth.didDocServiceModule
import org.hyperledger.ariesframework.toJsonString
import org.hyperledger.indy.sdk.non_secrets.WalletSearch
import org.hyperledger.indy.sdk.wallet.WalletItemNotFoundException
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass
import org.hyperledger.indy.sdk.non_secrets.WalletRecord as IndyWalletRecord

@Serializable
data class WalletRecord(
    val id: String,
    val type: String,
    val value: String,
    val tags: Tags,
)

@Serializable
data class WalletRecordList(
    val records: List<WalletRecord>?,
)

open class Repository<T : BaseRecord>(private val type: KClass<T>, val agent: Agent) {
    private val wallet = agent.wallet
    private val logger = LoggerFactory.getLogger(Repository::class.java)
    private val jsonFormat = Json { serializersModule = didDocServiceModule }

    private val DEFAULT_QUERY_OPTIONS = """
    {
        "retrieveType": true,
        "retrieveTags": true
    }
    """

    companion object {
        inline operator fun <reified T : BaseRecord> invoke(agent: Agent) = Repository(T::class, agent)
    }

    @OptIn(InternalSerializationApi::class)
    fun recordToInstance(record: WalletRecord): T {
        val instance = jsonFormat.decodeFromString(type.serializer(), record.value)

        instance.id = record.id
        instance.setTags(record.tags)

        return instance
    }

    @OptIn(InternalSerializationApi::class)
    open suspend fun save(record: T) {
        val value = jsonFormat.encodeToString(type.serializer(), record)
        val tags = record.getTags().toJsonString()
        IndyWalletRecord.add(wallet.indyWallet, type.simpleName!!, record.id, value, tags).await()
    }

    @OptIn(InternalSerializationApi::class)
    suspend fun update(record: T) {
        val value = jsonFormat.encodeToString(type.serializer(), record)
        val tags = record.getTags().toJsonString()
        IndyWalletRecord.updateTags(wallet.indyWallet, type.simpleName!!, record.id, tags).await()
        IndyWalletRecord.updateValue(wallet.indyWallet, type.simpleName!!, record.id, value).await()
    }

    suspend fun delete(record: T) {
        IndyWalletRecord.delete(wallet.indyWallet, type.simpleName!!, record.id).await()
    }

    @OptIn(InternalSerializationApi::class)
    suspend fun getById(id: String): T {
        val recordJson = IndyWalletRecord.get(wallet.indyWallet, type.simpleName!!, id, DEFAULT_QUERY_OPTIONS).await()
        val record = Json.decodeFromString<WalletRecord>(recordJson)

        return recordToInstance(record)
    }

    suspend fun getAll(): List<T> {
        return findByQuery("{}")
    }

    suspend fun findByQuery(query: String): List<T> {
        return try {
            val records = search(type.simpleName!!, query)
            records.map { recordToInstance(it) }
        } catch (e: Exception) {
            logger.debug("Query $query failed with error: ${e.message}")
            emptyList()
        }
    }

    private suspend fun search(type: String, query: String, limit: Int = Int.MAX_VALUE): List<WalletRecord> {
        var recordJson: String?
        try {
            val search = WalletSearch.open(wallet.indyWallet, type, query, DEFAULT_QUERY_OPTIONS).await()
            recordJson = search.fetchNextRecords(wallet.indyWallet, limit).await()
        } catch (e: Exception) {
            logger.error("Fetch records failed: ${e.message}")
            throw e
        }

        val recordList = Json { ignoreUnknownKeys = true }.decodeFromString(WalletRecordList.serializer(), recordJson)

        return recordList.records ?: emptyList()
    }

    suspend fun findById(id: String): T? {
        return try {
            getById(id)
        } catch (e: Exception) {
            if (e is WalletItemNotFoundException) {
                null
            } else {
                throw e
            }
        }
    }

    suspend fun findSingleByQuery(query: String): T? {
        val records = findByQuery(query)
        return when (records.size) {
            1 -> records[0]
            0 -> null
            else -> throw Exception("Multiple records found for query $query")
        }
    }

    suspend fun getSingleByQuery(query: String): T {
        val record = findSingleByQuery(query)
        return record ?: throw Exception("Record not found for query $query")
    }
}
