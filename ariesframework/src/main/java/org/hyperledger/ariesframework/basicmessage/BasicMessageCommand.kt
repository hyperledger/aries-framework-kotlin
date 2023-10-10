package org.hyperledger.ariesframework.basicmessage

import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.Dispatcher
import org.hyperledger.ariesframework.agent.MessageSerializer
import org.hyperledger.ariesframework.basicmessage.handlers.BasicMessageHandler
import org.hyperledger.ariesframework.basicmessage.messages.BasicMessage
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
