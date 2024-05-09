package org.hyperledger.ariesframework.agent.decorators

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.hyperledger.ariesframework.decodeBase64
import org.hyperledger.ariesframework.encodeBase64
import java.util.UUID

@Serializable
class AttachmentData(
    val base64: String? = null,
    val json: JsonObject? = null,
    val links: List<String>? = null,
    var jws: Jws? = null,
    val sha256: String? = null,
)

@Serializable
class Attachment(
    @SerialName("@id")
    val id: String,
    val description: String? = null,
    val filename: String? = null,
    @SerialName("mime-type")
    val mimetype: String? = null,
    @SerialName("lastmod_time")
    val lastModified: Instant? = null,
    @SerialName("byte_count")
    val byteCount: Int? = null,
    val data: AttachmentData,
) {
    fun getDataAsString(): String {
        return when {
            data.base64 != null -> String(data.base64.decodeBase64())
            data.json != null -> Json.encodeToString(data.json)
            else -> throw Exception("No attachment data found in `json` or `base64` data fields.")
        }
    }

    fun addJws(jws: JwsGeneralFormat) {
        if (data.jws == null) {
            data.jws = jws
            return
        }

        when (data.jws) {
            is JwsFlattenedFormat -> (data.jws as JwsFlattenedFormat).signatures.add(jws)
            is JwsGeneralFormat -> data.jws = JwsFlattenedFormat(arrayListOf(data.jws as JwsGeneralFormat, jws))
            else -> throw Exception("Unknown JWS type")
        }
    }

    companion object {
        fun fromData(data: ByteArray, id: String = UUID.randomUUID().toString()): Attachment {
            return Attachment(
                id = id,
                mimetype = "application/json",
                data = AttachmentData(base64 = data.encodeBase64()),
            )
        }
    }
}
