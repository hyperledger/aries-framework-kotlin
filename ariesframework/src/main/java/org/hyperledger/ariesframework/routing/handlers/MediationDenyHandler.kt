package org.hyperledger.ariesframework.routing.handlers

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.MessageHandler
import org.hyperledger.ariesframework.routing.messages.MediationDenyMessage

class MediationDenyHandler(val agent: Agent) : MessageHandler {
    override val messageType = MediationDenyMessage.type

    override suspend fun handle(messageContext: InboundMessageContext): OutboundMessage? {
        agent.mediationRecipient.processMediationDeny(messageContext)
        return null
    }
}
