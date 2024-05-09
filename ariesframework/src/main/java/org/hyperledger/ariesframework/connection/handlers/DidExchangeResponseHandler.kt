package org.hyperledger.ariesframework.connection.handlers

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.MessageHandler
import org.hyperledger.ariesframework.connection.messages.DidExchangeResponseMessage

class DidExchangeResponseHandler(val agent: Agent) : MessageHandler {
    override val messageType = DidExchangeResponseMessage.type

    override suspend fun handle(messageContext: InboundMessageContext): OutboundMessage? {
        val connectionRecord = agent.didExchangeService.processResponse(messageContext)
        if (connectionRecord.autoAcceptConnection == true || agent.agentConfig.autoAcceptConnections) {
            return agent.didExchangeService.createComplete(connectionRecord.id)
        }

        return null
    }
}
