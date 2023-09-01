package org.hyperledger.ariesframework.connection.models.didauth

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.hyperledger.ariesframework.connection.models.didauth.publicKey.PublicKey

@Serializable(with = EmbeddedAuthenticationSerializer::class)
class EmbeddedAuthentication(
    val publicKey: PublicKey,
) : Authentication()

// https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/serializers.md#delegating-serializers
class EmbeddedAuthenticationSerializer : KSerializer<EmbeddedAuthentication> {
    private val delegateSerializer = PublicKey.serializer()
    override val descriptor = SerialDescriptor("EmbeddedAuthentication", delegateSerializer.descriptor)

    override fun serialize(encoder: Encoder, value: EmbeddedAuthentication) {
        encoder.encodeSerializableValue(delegateSerializer, value.publicKey)
    }

    override fun deserialize(decoder: Decoder): EmbeddedAuthentication {
        val publicKey = decoder.decodeSerializableValue(delegateSerializer)
        return EmbeddedAuthentication(publicKey)
    }
}
