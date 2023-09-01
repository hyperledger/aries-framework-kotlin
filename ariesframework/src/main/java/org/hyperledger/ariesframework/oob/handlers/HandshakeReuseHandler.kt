package org.hyperledger.ariesframework.oob.handlers

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.MessageHandler
import org.hyperledger.ariesframework.oob.messages.HandshakeReuseMessage

class HandshakeReuseHandler(val agent: Agent) : MessageHandler {
    override val messageType = HandshakeReuseMessage.type

    override suspend fun handle(messageContext: InboundMessageContext): OutboundMessage? {
        val connection = messageContext.assertReadyConnection()
        val message = agent.outOfBandService.processHandshakeReuse(messageContext)

        return OutboundMessage(message, connection)
    }
}
