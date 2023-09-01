package org.hyperledger.ariesframework.oob

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.hyperledger.ariesframework.agent.MessageSerializer
import org.hyperledger.ariesframework.connection.messages.ConnectionInvitationMessage
import org.hyperledger.ariesframework.oob.messages.OutOfBandInvitation
import org.hyperledger.ariesframework.oob.models.InvitationType
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

object InvitationUrlParser {
    fun getInvitationType(invitationUrl: String): InvitationType {
        val url = URL(invitationUrl)
        val queryParams = url.query?.split("&")?.associate {
            val (key, value) = it.split("=")
            key to URLDecoder.decode(value, "UTF-8")
        }

        if (queryParams?.get("oob") != null) {
            return InvitationType.OOB
        } else if (queryParams?.get("c_i") != null || queryParams?.get("c_m") != null) {
            return InvitationType.Connection
        } else {
            return InvitationType.Unknown
        }
    }

    suspend fun parseUrl(url: String): Pair<OutOfBandInvitation?, ConnectionInvitationMessage?> {
        val type = getInvitationType(url)
        var invitation: ConnectionInvitationMessage? = null
        var outOfBandInvitation: OutOfBandInvitation? = null
        when (type) {
            InvitationType.Connection -> invitation = ConnectionInvitationMessage.fromUrl(url)
            InvitationType.OOB -> outOfBandInvitation = OutOfBandInvitation.fromUrl(url)
            else -> {
                val (oob, conn) = invitationFromShortUrl(url)
                outOfBandInvitation = oob
                invitation = conn
            }
        }

        return Pair(outOfBandInvitation, invitation)
    }

    private suspend fun invitationFromShortUrl(url: String): Pair<OutOfBandInvitation?, ConnectionInvitationMessage?> {
        var invitationJson = withContext(Dispatchers.IO) {
            URL(url).openConnection().run {
                this as HttpURLConnection
                if (contentType != "application/json") {
                    throw Exception("Invalid content-type from short url: $contentType")
                }
                inputStream.bufferedReader().use { it.readText() }
            }
        }
        invitationJson = OutOfBandInvitation.replaceLegacyDidSovWithNewDidCommPrefix(invitationJson)
        val message = MessageSerializer.decodeFromString(invitationJson)
        if (message.type == ConnectionInvitationMessage.type) {
            val invitation = Json.decodeFromString<ConnectionInvitationMessage>(invitationJson)
            return Pair(null, invitation)
        } else if (message.type.startsWith("https://didcomm.org/out-of-band/")) {
            val invitation = Json.decodeFromString<OutOfBandInvitation>(invitationJson)
            return Pair(invitation, null)
        } else {
            throw Exception("Invalid message type from short url: ${message.type}")
        }
    }
}
