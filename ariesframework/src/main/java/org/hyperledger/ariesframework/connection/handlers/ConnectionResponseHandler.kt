package org.hyperledger.ariesframework.connection.handlers

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.MessageHandler
import org.hyperledger.ariesframework.connection.messages.ConnectionResponseMessage

class ConnectionResponseHandler(val agent: Agent) : MessageHandler {
    override val messageType = ConnectionResponseMessage.type

    override suspend fun handle(messageContext: InboundMessageContext): OutboundMessage? {
        val connectionRecord = agent.connectionService.processResponse(messageContext)
        if (connectionRecord.autoAcceptConnection == true || agent.agentConfig.autoAcceptConnections) {
            return agent.connectionService.createTrustPing(connectionRecord.id, false)
        }

        return null
    }
}
