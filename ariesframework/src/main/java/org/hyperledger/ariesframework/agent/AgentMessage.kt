package org.hyperledger.ariesframework.agent

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import org.hyperledger.ariesframework.agent.decorators.ThreadDecorator
import org.hyperledger.ariesframework.agent.decorators.TransportDecorator
import org.hyperledger.ariesframework.connection.models.didauth.didDocServiceModule
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.reflect.KClass

@Serializable
open class AgentMessage(
    @SerialName("@id")
    var id: String,
    @SerialName("@type")
    var type: String,
    @SerialName("~thread")
    var thread: ThreadDecorator? = null,
    @SerialName("~transport")
    var transport: TransportDecorator? = null,
) {
    val threadId: String
        get() = thread?.threadId ?: id

    open fun requestResponse(): Boolean {
        return true
    }

    fun toJsonString(): String {
        return MessageSerializer.encodeToString(this)
    }

    fun replaceNewDidCommPrefixWithLegacyDidSov() {
        type = Dispatcher.replaceNewDidCommPrefixWithLegacyDidSov(type)
    }

    companion object {
        fun generateId(): String {
            return UUID.randomUUID().toString()
        }
    }
}

object MessageSerializer : JsonContentPolymorphicSerializer<AgentMessage>(AgentMessage::class) {
    private val serializers = mutableMapOf<String, KSerializer<AgentMessage>>()
    private val logger = LoggerFactory.getLogger(MessageSerializer::class.java)
    private val encoder = Json { serializersModule = didDocServiceModule }
    private val decoder = Json { ignoreUnknownKeys = true; serializersModule = didDocServiceModule }

    @OptIn(InternalSerializationApi::class)
    fun <T : AgentMessage> registerMessage(type: String, clazz: KClass<T>) {
        serializers[type] = clazz.serializer() as KSerializer<AgentMessage>
        serializers[Dispatcher.replaceNewDidCommPrefixWithLegacyDidSov(type)] = clazz.serializer() as KSerializer<AgentMessage>
    }

    override fun selectDeserializer(element: JsonElement): KSerializer<AgentMessage> {
        val type = element.jsonObject["@type"]?.jsonPrimitive?.content
        return if (serializers.containsKey(type)) {
            serializers[type]!!
        } else {
            logger.error("Message type $type is not registered for JSON decoding")
            AgentMessage.serializer()
        }
    }

    fun encodeToString(message: AgentMessage): String {
        if (!serializers.containsKey(message.type)) {
            logger.error("Message type ${message.type} is not registered for JSON encoding")
            return Json.encodeToString(message)
        }
        return encoder.encodeToString(serializers[message.type]!!, message)
    }

    fun decodeFromString(message: String): AgentMessage {
        return decoder.decodeFromString(this, message)
    }
}
