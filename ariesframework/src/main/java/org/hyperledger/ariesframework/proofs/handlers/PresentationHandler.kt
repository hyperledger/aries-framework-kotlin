package org.hyperledger.ariesframework.proofs.handlers

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.MessageHandler
import org.hyperledger.ariesframework.proofs.messages.PresentationMessage
import org.hyperledger.ariesframework.proofs.models.AutoAcceptProof

class PresentationHandler(val agent: Agent) : MessageHandler {
    override val messageType = PresentationMessage.type

    override suspend fun handle(messageContext: InboundMessageContext): OutboundMessage? {
        val presentationRecord = agent.proofService.processPresentation(messageContext)

        if (presentationRecord.autoAcceptProof == AutoAcceptProof.Always ||
            agent.agentConfig.autoAcceptProof == AutoAcceptProof.Always
        ) {
            val (message, _) = agent.proofService.createAck(presentationRecord)
            return OutboundMessage(message, messageContext.connection!!)
        }

        return null
    }
}
