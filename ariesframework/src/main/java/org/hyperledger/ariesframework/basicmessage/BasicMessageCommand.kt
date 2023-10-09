package org.hyperledger.ariesframework.basicmessage

import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.Dispatcher
import org.hyperledger.ariesframework.agent.MessageSerializer
import org.hyperledger.ariesframework.basicmessage.handlers.BasicMessageHandler
import org.hyperledger.ariesframework.basicmessage.messages.BasicMessage
import org.hyperledger.ariesframework.connection.handlers.ConnectionRequestHandler
import org.hyperledger.ariesframework.connection.handlers.ConnectionResponseHandler
import org.hyperledger.ariesframework.connection.handlers.TrustPingMessageHandler
import org.hyperledger.ariesframework.connection.messages.ConnectionInvitationMessage
import org.hyperledger.ariesframework.connection.messages.ConnectionRequestMessage
import org.hyperledger.ariesframework.connection.messages.ConnectionResponseMessage
import org.hyperledger.ariesframework.connection.messages.TrustPingMessage
import org.slf4j.LoggerFactory

class BasicMessageCommand(val agent: Agent, private val dispatcher: Dispatcher) {
    private val logger = LoggerFactory.getLogger(BasicMessageCommand::class.java)

    init {
        registerHandlers(dispatcher)
        registerMessages()
    }

    private fun registerHandlers(dispatcher: Dispatcher) {
        dispatcher.registerHandler(BasicMessageHandler(agent))
    }

    private fun registerMessages() {
        MessageSerializer.registerMessage(BasicMessage.type, BasicMessage::class)
    }
}