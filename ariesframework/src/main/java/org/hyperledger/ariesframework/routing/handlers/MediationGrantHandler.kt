package org.hyperledger.ariesframework.routing.handlers

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.MessageHandler
import org.hyperledger.ariesframework.routing.messages.MediationGrantMessage

class MediationGrantHandler(val agent: Agent) : MessageHandler {
    override val messageType = MediationGrantMessage.type

    override suspend fun handle(messageContext: InboundMessageContext): OutboundMessage? {
        agent.mediationRecipient.processMediationGrant(messageContext)
        return null
    }
}
