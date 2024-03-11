package org.hyperledger.ariesframework.oob.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.hyperledger.ariesframework.connection.models.didauth.DidCommService
import org.hyperledger.ariesframework.util.DIDParser

@Serializable(with = OutOfBandDidCommServiceSerializer::class)
abstract class OutOfBandDidCommService {
    abstract fun asDidCommService(): DidCommService?
}

object OutOfBandDidCommServiceSerializer : JsonContentPolymorphicSerializer<OutOfBandDidCommService>(OutOfBandDidCommService::class) {
    override fun selectDeserializer(element: JsonElement) = when (element is JsonPrimitive) {
        true -> PublicDidService.serializer()
        false -> OutOfBandDidDocumentService.serializer()
    }
}

@Serializable
class OutOfBandDidDocumentService(
    val id: String,
    val type: String = "did-communication",
    val serviceEndpoint: String,
    val recipientKeys: List<String>,
    val routingKeys: List<String>? = null,
    val accept: List<String>? = null,
) : OutOfBandDidCommService() {
    override fun asDidCommService(): DidCommService? {
        return DidCommService(
            id,
            serviceEndpoint,
            DIDParser.convertDidKeysToVerkeys(recipientKeys),
            DIDParser.convertDidKeysToVerkeys(routingKeys ?: emptyList()),
        )
    }
}

@Serializable(with = PublicDidServiceSerializer::class)
class PublicDidService(
    val did: String,
) : OutOfBandDidCommService() {
    override fun asDidCommService(): DidCommService? {
        return null
    }
}

class PublicDidServiceSerializer : KSerializer<PublicDidService> {
    private val delegateSerializer = String.serializer()
    override val descriptor = PrimitiveSerialDescriptor("PublicDidService", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: PublicDidService) {
        encoder.encodeSerializableValue(delegateSerializer, value.did)
    }

    override fun deserialize(decoder: Decoder): PublicDidService {
        val did = decoder.decodeSerializableValue(delegateSerializer)
        return PublicDidService(did)
    }
}
