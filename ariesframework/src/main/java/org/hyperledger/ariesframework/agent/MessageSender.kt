package org.hyperledger.ariesframework.agent

import org.hyperledger.ariesframework.EnvelopeKeys
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.OutboundPackage
import org.hyperledger.ariesframework.agent.decorators.ThreadDecorator
import org.hyperledger.ariesframework.agent.decorators.TransportDecorator
import org.hyperledger.ariesframework.connection.messages.TrustPingMessage
import org.hyperledger.ariesframework.connection.models.ConnectionRole
import org.hyperledger.ariesframework.connection.models.didauth.DidComm
import org.hyperledger.ariesframework.connection.models.didauth.DidCommService
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.hyperledger.ariesframework.routing.messages.BatchPickupMessage
import org.hyperledger.ariesframework.routing.messages.ForwardMessage
import org.slf4j.LoggerFactory

class MessageSender(val agent: Agent) {
    private val logger = LoggerFactory.getLogger(MessageSender::class.java)
    private var defaultOutboundTransport: OutboundTransport? = null
    private val httpOutboundTransport = HttpOutboundTransport(agent)
    private val wsOutboundTransport = WsOutboundTransport(agent)

    fun setOutboundTransport(outboundTransport: OutboundTransport) {
        this.defaultOutboundTransport = outboundTransport
    }

    private fun outboundTransportForEndpoint(endpoint: String): OutboundTransport? {
        if (defaultOutboundTransport != null) {
            return defaultOutboundTransport
        } else if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
            return httpOutboundTransport
        } else if (endpoint.startsWith("ws://") || endpoint.startsWith("wss://")) {
            return wsOutboundTransport
        } else {
            return null
        }
    }

    private fun decorateMessage(message: OutboundMessage): AgentMessage {
        val agentMessage = message.payload
        // If the agent is initialized, and the message is a TrustPing message, set the transport to "all".
        // This enables the agent to receive undelivered messages from the mediator.
        // For this to work, requestResponse must be set to false. The mediator will only return queued
        // messages if the trust ping message doesn't expect a response.
        // This is tested with aca-py mediator 0.4.x
        if (agent.isInitialized() && agentMessage.type == TrustPingMessage.type) {
            agentMessage.transport = TransportDecorator("all")
        }
        if (
            agentMessage.transport == null &&
            agentMessage.requestResponse() &&
            (agent.agentConfig.useReturnRoute || !agent.isInitialized() || agentMessage.type == BatchPickupMessage.type)
        ) {
            agentMessage.transport = TransportDecorator("all")
        }

        if (agent.agentConfig.useLegacyDidSovPrefix) {
            agentMessage.replaceNewDidCommPrefixWithLegacyDidSov()
        }

        // If the message is a response to an out-of-band invitation, set the parent thread id.
        // We should not override the parent thread id if it is already set, because it may be
        // a response to a different invitation. For example, a handshake-reuse message sent
        // over an existing connection created from a different out-of-band invitation.
        message.connection.outOfBandInvitation?.let {
            val thread = agentMessage.thread ?: ThreadDecorator()
            if (thread.parentThreadId == null) {
                thread.parentThreadId = it.id
                agentMessage.thread = thread
            }
        }

        return agentMessage
    }

    suspend fun send(message: OutboundMessage, endpointPrefix: String? = null) {
        val agentMessage = decorateMessage(message)
        val services = findDidCommServices(message.connection)
        if (services.isEmpty()) {
            logger.error("Cannot find services for message of type ${agentMessage.type}")
        }

        for (service in services) {
            if (endpointPrefix != null && !service.serviceEndpoint.startsWith(endpointPrefix)) {
                continue
            }
            logger.debug("Send outbound message of type ${agentMessage.type} to endpoint ${service.serviceEndpoint}")
            if (endpointPrefix == null && outboundTransportForEndpoint(service.serviceEndpoint) == null) {
                logger.debug("endpoint is not supported")
                continue
            }
            try {
                sendMessageToService(agentMessage, service, message.connection.verkey, message.connection.id)
                return
            } catch (e: Exception) {
                logger.debug("Sending outbound message to service ${service.serviceEndpoint} failed with the following error: ${e.message}")
            }
        }

        throw Exception("Message is undeliverable to connection ${message.connection.id}")
    }

    private fun findDidCommServices(connection: ConnectionRecord): List<DidComm> {
        if (connection.theirDidDoc != null) {
            return connection.theirDidDoc!!.didCommServices()
        }

        if (connection.role == ConnectionRole.Invitee) {
            if (connection.invitation != null && connection.invitation!!.serviceEndpoint != null) {
                val service = DidCommService(
                    "${connection.id}-invitation",
                    connection.invitation!!.serviceEndpoint!!,
                    connection.invitation!!.recipientKeys ?: emptyList(),
                    connection.invitation!!.routingKeys ?: emptyList(),
                )
                return listOf(service)
            }
            if (connection.outOfBandInvitation != null) {
                return connection.outOfBandInvitation!!.services.mapNotNull { it.asDidCommService() }
            }
        }

        return emptyList()
    }

    private suspend fun sendMessageToService(message: AgentMessage, service: DidComm, senderKey: String, connectionId: String) {
        val keys = EnvelopeKeys(service.recipientKeys, service.routingKeys ?: emptyList(), senderKey)

        val outboundPackage = packMessage(message, keys, service.serviceEndpoint, connectionId)
        val outboundTransport = outboundTransportForEndpoint(service.serviceEndpoint)
            ?: throw Exception("No outbound transport found for endpoint ${service.serviceEndpoint}")
        outboundTransport.sendPackage(outboundPackage)
    }

    private suspend fun packMessage(message: AgentMessage, keys: EnvelopeKeys, endpoint: String, connectionId: String): OutboundPackage {
        var encryptedMessage = agent.wallet.pack(message, keys.recipientKeys, keys.senderKey)

        var recipientKeys = keys.recipientKeys
        for (routingKey in keys.routingKeys) {
            val forwardMessage = ForwardMessage(recipientKeys[0], encryptedMessage)
            if (agent.agentConfig.useLegacyDidSovPrefix) {
                forwardMessage.replaceNewDidCommPrefixWithLegacyDidSov()
            }
            recipientKeys = listOf(routingKey)
            encryptedMessage = agent.wallet.pack(forwardMessage, recipientKeys, keys.senderKey)
        }

        return OutboundPackage(encryptedMessage, message.requestResponse(), endpoint, connectionId)
    }

    fun close() {
        wsOutboundTransport.closeSocket()
    }
}
