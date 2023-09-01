package org.hyperledger.ariesframework.agent

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.OutboundMessage

interface MessageHandler {
    val messageType: String
    suspend fun handle(messageContext: InboundMessageContext): OutboundMessage?
}
