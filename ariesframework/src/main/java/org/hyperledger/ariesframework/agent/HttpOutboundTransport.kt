package org.hyperledger.ariesframework.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.hyperledger.ariesframework.EncryptedMessage
import org.hyperledger.ariesframework.OutboundPackage
import org.slf4j.LoggerFactory

enum class DidCommMimeType(val value: String) {
    V0("application/ssi-agent-wire"),
    V1("application/didcomm-envelope-enc"),
}

class HttpOutboundTransport(val agent: Agent) : OutboundTransport {
    private val logger = LoggerFactory.getLogger(HttpOutboundTransport::class.java)

    override suspend fun sendPackage(_package: OutboundPackage) {
        logger.debug("Sending outbound message to endpoint: {}", _package.endpoint)

        val responseText = withContext(Dispatchers.IO) {
            val request = okhttp3.Request.Builder()
                .url(_package.endpoint)
                .post(
                    Json.encodeToString(_package.payload)
                        .toByteArray() // prevent okHttp from adding charset=utf-8 to the content type
                        .toRequestBody(DidCommMimeType.V1.value.toMediaType()),
                )
                .build()
            val response = AgentHttpClient.client.newCall(request).execute()
            logger.debug("response with status code: {}", response.code)
            response.body?.string() ?: ""
        }

        if (responseText.isNotEmpty()) {
            val encryptedMessage = Json.decodeFromString<EncryptedMessage>(responseText)
            agent.receiveMessage(encryptedMessage)
        } else if (_package.responseRequested) {
            logger.debug("Requested response but got no data. Will initiate message pickup if necessary.")
            GlobalScope.launch {
                delay(agent.agentConfig.mediatorEmptyReturnRetryInterval * 1000)
                agent.mediationRecipient.pickupMessages()
            }
        } else {
            logger.debug("No data received")
        }
    }
}
