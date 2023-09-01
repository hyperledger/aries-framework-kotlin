package org.hyperledger.ariesframework.oob.messages

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.hyperledger.ariesframework.agent.AgentMessage
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.agent.decorators.AttachmentData
import org.hyperledger.ariesframework.decodeBase64url
import org.hyperledger.ariesframework.encodeBase64
import org.hyperledger.ariesframework.oob.models.HandshakeProtocol
import org.hyperledger.ariesframework.oob.models.OutOfBandDidCommService
import org.hyperledger.ariesframework.oob.models.OutOfBandDidDocumentService
import org.hyperledger.ariesframework.util.DIDParser
import java.net.URL
import java.net.URLDecoder

@Serializable
class OutOfBandInvitation(
    var label: String,
    @SerialName("goal_code")
    var goalCode: String? = null,
    var goal: String? = null,
    var accept: List<String>? = null,
    @SerialName("handshake_protocols")
    var handshakeProtocols: List<HandshakeProtocol>? = null,
    @SerialName("requests~attach")
    var requests: MutableList<Attachment>? = null,
    @EncodeDefault
    var services: List<OutOfBandDidCommService>,
    var imageUrl: String? = null,
) : AgentMessage(generateId(), OutOfBandInvitation.type) {

    init {
        require(services.isNotEmpty()) {
            "Decoding out-of-band invitation failed: no services found"
        }
    }

    fun addRequest(message: AgentMessage) {
        if (requests == null) {
            requests = mutableListOf()
        }
        val requestAttachment = Attachment(
            id = generateId(),
            mimetype = "application/json",
            data = AttachmentData(message.toJsonString().encodeToByteArray().encodeBase64()),
        )
        requests!!.add(requestAttachment)
    }

    fun getRequestsJson(): List<String> {
        return requests?.map { it.getDataAsString() } ?: emptyList()
    }

    fun toUrl(domain: String): String {
        val invitationJson = Json.encodeToString(this)
        return "$domain?oob=${invitationJson.encodeToByteArray().encodeBase64()}"
    }

    fun fingerprints(): List<String> {
        return services
            .map {
                if (it is OutOfBandDidDocumentService) {
                    it.recipientKeys
                } else {
                    emptyList()
                }
            }
            .reduce { acc, list -> acc + list }
            .map { recipientKeys ->
                DIDParser.getMethodId(recipientKeys)
            }
    }

    fun invitationKey(): String? {
        val fingerprints = fingerprints()
        if (fingerprints.isEmpty()) {
            return null
        }
        return DIDParser.convertFingerprintToVerkey(fingerprints[0])
    }

    companion object {
        const val type = "https://didcomm.org/out-of-band/1.1/invitation"

        fun fromUrl(invitationUrl: String): OutOfBandInvitation {
            val url = URL(invitationUrl)
            val queryParams = url.query?.split("&")?.associate {
                val (key, value) = it.split("=")
                key to URLDecoder.decode(value, "UTF-8")
            }

            val encodedInvitation = queryParams?.get("oob")
            if (encodedInvitation != null) {
                val invitationJson = encodedInvitation.decodeBase64url().toString(Charsets.UTF_8)
                val replaced = replaceLegacyDidSovWithNewDidCommPrefix(invitationJson)
                return Json.decodeFromString(replaced)
            }
            throw Exception("InvitationUrl is invalid. It needs to contain one, and only one, of the following parameters; `oob`")
        }

        fun fromJson(json: String): OutOfBandInvitation {
            val replaced = replaceLegacyDidSovWithNewDidCommPrefix(json)
            return Json.decodeFromString<OutOfBandInvitation>(replaced)
        }

        fun replaceLegacyDidSovWithNewDidCommPrefix(message: String): String {
            val didSovPrefix = "did:sov:BzCbsNYhMrjHiqZDTUASHg;spec"
            val didCommPrefix = "https://didcomm.org"

            return message.replace(didSovPrefix, didCommPrefix)
        }
    }
}
