package org.hyperledger.ariesframework.agent

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import org.hyperledger.ariesframework.EncryptedMessage
import org.hyperledger.ariesframework.OutboundPackage
import org.slf4j.LoggerFactory

class WsOutboundTransport(val agent: Agent) : OutboundTransport, WebSocketListener() {
    private val logger = LoggerFactory.getLogger(WsOutboundTransport::class.java)
    private var socket: WebSocket? = null
    private var endpoint = ""
    private val lock = Mutex()
    private val CLOSE_BY_CLIENT = 3000

    override suspend fun sendPackage(_package: OutboundPackage) {
        lock.withLock {
            logger.debug("Sending outbound package to endpoint: {}", _package.endpoint)
            logger.debug("_package.responseRequested: {}", _package.responseRequested)
            if (socket == null || endpoint != _package.endpoint) {
                socket = createSocket(_package.endpoint)
            }
            if (!socket!!.send(Json.encodeToString(_package.payload).encodeUtf8())) {
                throw Exception("Failed to send outbound package to endpoint ${_package.endpoint}")
            }
        }
    }

    private suspend fun createSocket(endpoint: String): WebSocket {
        logger.debug("Creating socket for endpoint: {}", endpoint)
        socket?.close(CLOSE_BY_CLIENT, null)
        val request = okhttp3.Request.Builder().url(endpoint).build()
        socket = AgentHttpClient.client.newWebSocket(request, this)
        this.endpoint = endpoint
        return socket!!
    }

    fun closeSocket() {
        if (socket != null) {
            socket!!.close(CLOSE_BY_CLIENT, null)
            socket = null
        }
    }

    override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
        logger.debug("Socket open for endpoint: {}", endpoint)
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        logger.debug("Agent ${agent.agentConfig.label} received a message string")
        val encryptedMessage = Json.decodeFromString<EncryptedMessage>(text)
        GlobalScope.launch {
            agent.receiveMessage(encryptedMessage)
        }
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        logger.debug("Agent ${agent.agentConfig.label} received a message bytes.")
        val encryptedMessage = Json.decodeFromString<EncryptedMessage>(bytes.utf8())
        GlobalScope.launch {
            agent.receiveMessage(encryptedMessage)
        }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        logger.debug("Socket closing with code, reason: {}, {}", code, reason)
        if (code != CLOSE_BY_CLIENT) {
            socket = null
        }
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        logger.debug("Socket closed for endpoint: {}", endpoint)
        if (code != CLOSE_BY_CLIENT) {
            socket = null
        }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
        logger.error("Socket failure: {}", t.message)
        socket = null
    }
}
