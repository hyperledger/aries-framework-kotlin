package org.hyperledger.ariesframework.agent.decorators

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

@Serializable
class JwsGeneralFormat(
    val header: Map<String, String>? = null,
    val signature: String,
    val protected: String,
) : Jws()

@Serializable
class JwsFlattenedFormat(val signatures: ArrayList<JwsGeneralFormat>) : Jws()

@Serializable(with = JwsSerializer::class)
abstract class Jws

object JwsSerializer : JsonContentPolymorphicSerializer<Jws>(Jws::class) {
    override fun selectDeserializer(element: JsonElement) = when (element.jsonObject.size) {
        1 -> JwsFlattenedFormat.serializer()
        else -> JwsGeneralFormat.serializer()
    }
}
