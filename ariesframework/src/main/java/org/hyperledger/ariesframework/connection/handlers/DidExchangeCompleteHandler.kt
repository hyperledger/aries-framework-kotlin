package org.hyperledger.ariesframework.connection.handlers

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.MessageHandler
import org.hyperledger.ariesframework.connection.messages.DidExchangeCompleteMessage
import org.hyperledger.ariesframework.connection.models.ConnectionState

class DidExchangeCompleteHandler(val agent: Agent) : MessageHandler {
    override val messageType = DidExchangeCompleteMessage.type

    override suspend fun handle(messageContext: InboundMessageContext): OutboundMessage? {
        val connection = messageContext.connection
        if (connection != null && connection.state == ConnectionState.Responded) {
            agent.connectionService.updateState(connection, ConnectionState.Complete)
        }
        return null
    }
}
