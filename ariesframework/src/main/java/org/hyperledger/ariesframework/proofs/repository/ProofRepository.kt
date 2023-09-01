package org.hyperledger.ariesframework.proofs.repository

import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.storage.Repository

class ProofRepository(agent: Agent) : Repository<ProofExchangeRecord>(ProofExchangeRecord::class, agent) {
    suspend fun getByThreadAndConnectionId(threadId: String, connectionId: String?): ProofExchangeRecord {
        return if (connectionId != null) {
            getSingleByQuery("{\"threadId\": \"$threadId\", \"connectionId\": \"$connectionId\"}")
        } else {
            getSingleByQuery("{\"threadId\": \"$threadId\"}")
        }
    }
}
