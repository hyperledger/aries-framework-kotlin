package org.hyperledger.ariesframework.problemreports

import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.Dispatcher
import org.hyperledger.ariesframework.agent.MessageSerializer
import org.hyperledger.ariesframework.problemreports.handlers.ProblemReportHandler
import org.hyperledger.ariesframework.problemreports.messages.CredentialProblemReportMessage
import org.hyperledger.ariesframework.problemreports.messages.MediationProblemReportMessage
import org.hyperledger.ariesframework.problemreports.messages.PresentationProblemReportMessage
import org.slf4j.LoggerFactory

class ProblemReportsCommand(val agent: Agent, private val dispatcher: Dispatcher) {
    private val logger = LoggerFactory.getLogger(ProblemReportsCommand::class.java)

    init {
        registerHandlers(dispatcher)
        registerMessages()
    }

    private fun registerHandlers(dispatcher: Dispatcher) {
        dispatcher.registerHandler(ProblemReportHandler(agent, PresentationProblemReportMessage.type))
        dispatcher.registerHandler(ProblemReportHandler(agent, CredentialProblemReportMessage.type))
        dispatcher.registerHandler(ProblemReportHandler(agent, MediationProblemReportMessage.type))
    }

    private fun registerMessages() {
        MessageSerializer.registerMessage(PresentationProblemReportMessage.type, PresentationProblemReportMessage::class)
        MessageSerializer.registerMessage(CredentialProblemReportMessage.type, CredentialProblemReportMessage::class)
        MessageSerializer.registerMessage(MediationProblemReportMessage.type, MediationProblemReportMessage::class)
    }
}
