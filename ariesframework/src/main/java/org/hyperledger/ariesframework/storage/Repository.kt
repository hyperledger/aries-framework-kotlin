package org.hyperledger.ariesframework.storage

import askar_uniffi.AskarEntry
import askar_uniffi.AskarEntryOperation
import askar_uniffi.ErrorCode
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.hyperledger.ariesframework.Tags
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.connection.models.didauth.didDocServiceModule
import org.hyperledger.ariesframework.toJsonString
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

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
    fun recordToInstance(record: AskarEntry): T {
        val instance = jsonFormat.decodeFromString(type.serializer(), String(record.value()))

        instance.id = record.name()
        instance.setTags(record.tags())

        return instance
    }

    @OptIn(InternalSerializationApi::class)
    open suspend fun save(record: T) {
        val value = jsonFormat.encodeToString(type.serializer(), record).toByteArray()
        val tags = record.getTags().toJsonString()
        wallet.session!!.update(AskarEntryOperation.INSERT, type.simpleName!!, record.id, value, tags, null)
    }

    @OptIn(InternalSerializationApi::class)
    suspend fun update(record: T) {
        val value = jsonFormat.encodeToString(type.serializer(), record).toByteArray()
        val tags = record.getTags().toJsonString()
        wallet.session!!.update(AskarEntryOperation.REPLACE, type.simpleName!!, record.id, value, tags, null)
    }

    suspend fun delete(record: T) {
        wallet.session!!.update(AskarEntryOperation.REMOVE, type.simpleName!!, record.id, ByteArray(0), null, null)
    }

    suspend fun deleteById(id: String) {
        wallet.session!!.update(AskarEntryOperation.REMOVE, type.simpleName!!, id, ByteArray(0), null, null)
    }

    @OptIn(InternalSerializationApi::class)
    suspend fun getById(id: String): T {
        val record = wallet.session!!.fetch(type.simpleName!!, id, false)
            ?: throw ErrorCode.NotFound("Record not found")
        return recordToInstance(record)
    }

    suspend fun getAll(): List<T> {
        return findByQuery("{}")
    }

    suspend fun findByQuery(query: String): List<T> {
        return try {
            val scan = wallet.store!!.scan(null, type.simpleName!!, query, null, null)
            val records = scan.fetchAll()
            records.map { recordToInstance(it) }
        } catch (e: Exception) {
            logger.debug("Query $query failed with error: ${e.message}")
            emptyList()
        }
    }

    suspend fun findById(id: String): T? {
        val record = wallet.session!!.fetch(type.simpleName!!, id, false)
            ?: return null
        return recordToInstance(record)
    }

    suspend fun findSingleByQuery(query: String): T? {
        val records = findByQuery(query)
        return when (records.size) {
            1 -> records[0]
            0 -> null
            else -> throw ErrorCode.Duplicate("Multiple records found for query $query")
        }
    }

    suspend fun getSingleByQuery(query: String): T {
        val record = findSingleByQuery(query)
        return record ?: throw ErrorCode.NotFound("Record not found for query $query")
    }
}
