package org.hyperledger.ariesframework.connection.messages

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.hyperledger.ariesframework.agent.AgentMessage
import org.hyperledger.ariesframework.decodeBase64url
import org.hyperledger.ariesframework.encodeBase64url
import java.net.URL
import java.net.URLDecoder

@Serializable
class ConnectionInvitationMessage(
    var label: String,
    var imageUrl: String? = null,
    var did: String? = null,
    var recipientKeys: List<String>? = null,
    var serviceEndpoint: String? = null,
    var routingKeys: List<String>? = null,
) : AgentMessage(generateId(), ConnectionInvitationMessage.type) {

    init {
        require(did != null || (recipientKeys != null && recipientKeys!!.isNotEmpty() && serviceEndpoint != null)) {
            "Both did and inline keys / endpoint are missing"
        }
    }

    companion object {
        const val type = "https://didcomm.org/connections/1.0/invitation"

        fun fromUrl(invitationUrl: String): ConnectionInvitationMessage {
            val url = URL(invitationUrl)
            val queryParams = url.query?.split("&")?.associate {
                val (key, value) = it.split("=")
                key to URLDecoder.decode(value, "UTF-8")
            }

            val encodedInvitation = queryParams?.get("c_i") ?: queryParams?.get("d_m")
            if (encodedInvitation != null) {
                val invitationJson = encodedInvitation.decodeBase64url().toString(Charsets.UTF_8)
                return Json.decodeFromString<ConnectionInvitationMessage>(invitationJson)
            }
            throw Exception("InvitationUrl is invalid. It needs to contain one, and only one, of the following parameters; `c_i` or `d_m`")
        }
    }

    fun toUrl(domain: String): String {
        val invitationJson = Json.encodeToString(this)
        val encodedInvitation = invitationJson.toByteArray().encodeBase64url()
        return "$domain?c_i=$encodedInvitation"
    }
}
