package org.hyperledger.ariesframework.connection.handlers

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.MessageHandler
import org.hyperledger.ariesframework.connection.messages.ConnectionRequestMessage

class ConnectionRequestHandler(val agent: Agent) : MessageHandler {
    override val messageType = ConnectionRequestMessage.type

    override suspend fun handle(messageContext: InboundMessageContext): OutboundMessage? {
        val connectionRecord = agent.connectionService.processRequest(messageContext)
        if (connectionRecord.autoAcceptConnection == true || agent.agentConfig.autoAcceptConnections) {
            return agent.connectionService.createResponse(connectionRecord.id)
        }

        return null
    }
}
