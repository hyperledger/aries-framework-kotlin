package org.hyperledger.ariesframework.agent

import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.hyperledger.ariesframework.credentials.messages.CredentialProblemReportMessage
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.oob.repository.OutOfBandRecord
import org.hyperledger.ariesframework.proofs.messages.PresentationProblemReportMessage
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord
import org.hyperledger.ariesframework.routing.repository.MediationRecord

sealed interface AgentEvents {
    class ConnectionEvent(val record: ConnectionRecord) : AgentEvents
    class MediationEvent(val record: MediationRecord) : AgentEvents
    class OutOfBandEvent(val record: OutOfBandRecord) : AgentEvents
    class CredentialEvent(val record: CredentialExchangeRecord) : AgentEvents
    class ProofEvent(val record: ProofExchangeRecord) : AgentEvents
    class BasicMessageEvent(val message: String) : AgentEvents
    class CredentialProblemReportEvent(val message: CredentialProblemReportMessage) : AgentEvents
    class PresentationProblemReportEvent(val message: PresentationProblemReportMessage) : AgentEvents
}
