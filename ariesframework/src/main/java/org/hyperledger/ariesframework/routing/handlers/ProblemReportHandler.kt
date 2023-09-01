package org.hyperledger.ariesframework.routing.handlers

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.MessageHandler
import org.hyperledger.ariesframework.agent.MessageSerializer
import org.hyperledger.ariesframework.routing.messages.ProblemReportMessage
import org.slf4j.LoggerFactory

class ProblemReportHandler(val agent: Agent) : MessageHandler {
    override val messageType = ProblemReportMessage.type
    private val logger = LoggerFactory.getLogger(ProblemReportHandler::class.java)

    override suspend fun handle(messageContext: InboundMessageContext): OutboundMessage? {
        val message = MessageSerializer.decodeFromString(messageContext.plaintextMessage) as ProblemReportMessage
        logger.error(
            "Problem report of type $messageType received: ${message.description.en}" +
                ", Fix hint: ${message.fixHint?.en ?: "none"}",
        )
        return null
    }
}
