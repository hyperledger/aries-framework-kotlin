package org.hyperledger.ariesframework.oob.handlers

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.MessageHandler
import org.hyperledger.ariesframework.oob.messages.HandshakeReuseAcceptedMessage

class HandshakeReuseAcceptedHandler(val agent: Agent) : MessageHandler {
    override val messageType = HandshakeReuseAcceptedMessage.type

    override suspend fun handle(messageContext: InboundMessageContext): OutboundMessage? {
        messageContext.assertReadyConnection()
        agent.outOfBandService.processHandshakeReuseAccepted(messageContext)

        return null
    }
}
