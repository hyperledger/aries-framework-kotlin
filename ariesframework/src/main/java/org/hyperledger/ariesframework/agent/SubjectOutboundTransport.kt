package org.hyperledger.ariesframework.agent

import org.hyperledger.ariesframework.OutboundPackage
import org.slf4j.LoggerFactory

class SubjectOutboundTransport(private val subject: Agent) : OutboundTransport {
    private val logger = LoggerFactory.getLogger(SubjectOutboundTransport::class.java)

    override suspend fun sendPackage(_package: OutboundPackage) {
        logger.debug("Sending outbound message to subject ${subject.agentConfig.label}")
        subject.receiveMessage(_package.payload)
    }
}
