package org.hyperledger.ariesframework.proofs.handlers

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.AgentEvents
import org.hyperledger.ariesframework.agent.MessageHandler
import org.hyperledger.ariesframework.proofs.messages.PresentationProblemReportMessage

class PresentationProblemReportHandler(val agent: Agent) : MessageHandler {
    override val messageType = PresentationProblemReportMessage.type

    override suspend fun handle(messageContext: InboundMessageContext): OutboundMessage? {
        val message = messageContext.message as PresentationProblemReportMessage
        agent.eventBus.publish(AgentEvents.PresentationProblemReportEvent(message))
        return null
    }
}
