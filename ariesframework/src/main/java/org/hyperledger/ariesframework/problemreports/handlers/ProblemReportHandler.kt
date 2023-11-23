package org.hyperledger.ariesframework.problemreports.handlers

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.AgentEvents
import org.hyperledger.ariesframework.agent.MessageHandler
import org.hyperledger.ariesframework.problemreports.messages.BaseProblemReportMessage

class ProblemReportHandler(val agent: Agent, override val messageType: String) : MessageHandler {
    override suspend fun handle(messageContext: InboundMessageContext): OutboundMessage? {
        val message = messageContext.message as BaseProblemReportMessage
        agent.eventBus.publish(AgentEvents.ProblemReportEvent(message))
        return null
    }
}
