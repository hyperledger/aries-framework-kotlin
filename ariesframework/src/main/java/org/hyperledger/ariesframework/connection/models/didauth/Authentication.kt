package org.hyperledger.ariesframework.connection.models.didauth

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

@Serializable(with = AuthenticationSerializer::class)
abstract class Authentication

object AuthenticationSerializer : JsonContentPolymorphicSerializer<Authentication>(Authentication::class) {
    override fun selectDeserializer(element: JsonElement) = when (element.jsonObject.size) {
        2 -> ReferencedAuthentication.serializer()
        else -> EmbeddedAuthentication.serializer()
    }
}
