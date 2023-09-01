package org.hyperledger.ariesframework.credentials.handlers

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.MessageHandler
import org.hyperledger.ariesframework.credentials.messages.CredentialAckMessage

class CredentialAckHandler(val agent: Agent) : MessageHandler {
    override val messageType = CredentialAckMessage.type

    override suspend fun handle(messageContext: InboundMessageContext): OutboundMessage? {
        agent.credentialService.processAck(messageContext)
        return null
    }
}
