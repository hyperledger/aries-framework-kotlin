package org.hyperledger.ariesframework.agent

import org.hyperledger.ariesframework.DecryptedMessageContext
import org.hyperledger.ariesframework.EncryptedMessage
import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.slf4j.LoggerFactory

class MessageReceiver(val agent: Agent) {
    private val logger = LoggerFactory.getLogger(MessageReceiver::class.java)

    suspend fun receiveMessage(encryptedMessage: EncryptedMessage) {
        try {
            val decryptedMessage = agent.wallet.unpack(encryptedMessage)
            val connection = findConnectionByMessageKeys(decryptedMessage)
            val message = MessageSerializer.decodeFromString(decryptedMessage.plaintextMessage)
            val messageContext = InboundMessageContext(
                message,
                decryptedMessage.plaintextMessage,
                connection,
                decryptedMessage.senderKey,
                decryptedMessage.recipientKey,
            )
            agent.dispatcher.dispatch(messageContext)
        } catch (e: Exception) {
            logger.error("failed to receive message: $e")
        }
    }

    suspend fun receivePlaintextMessage(plaintextMessage: String, connection: ConnectionRecord) {
        try {
            val message = MessageSerializer.decodeFromString(plaintextMessage)
            val messageContext = InboundMessageContext(
                message,
                plaintextMessage,
                connection,
                null,
                null,
            )
            agent.dispatcher.dispatch(messageContext)
        } catch (e: Exception) {
            logger.error("failed to receive message: $e")
        }
    }

    private suspend fun findConnectionByMessageKeys(decryptedMessage: DecryptedMessageContext): ConnectionRecord? {
        return agent.connectionService.findByKeys(
            decryptedMessage.senderKey ?: "",
            decryptedMessage.recipientKey ?: "",
        )
    }
}
