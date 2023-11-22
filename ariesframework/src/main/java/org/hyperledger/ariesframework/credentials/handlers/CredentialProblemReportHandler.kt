package org.hyperledger.ariesframework.credentials.handlers

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.AgentEvents
import org.hyperledger.ariesframework.agent.MessageHandler
import org.hyperledger.ariesframework.credentials.messages.CredentialProblemReportMessage

class CredentialProblemReportHandler(val agent: Agent) : MessageHandler {
    override val messageType = CredentialProblemReportMessage.type

    override suspend fun handle(messageContext: InboundMessageContext): OutboundMessage? {
        val message = messageContext.message as CredentialProblemReportMessage
        agent.eventBus.publish(AgentEvents.CredentialProblemReportEvent(message))
        return null
    }
}
