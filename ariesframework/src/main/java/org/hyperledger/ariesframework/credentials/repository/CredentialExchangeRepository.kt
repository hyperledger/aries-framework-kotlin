package org.hyperledger.ariesframework.credentials.repository

import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.storage.Repository

class CredentialExchangeRepository(agent: Agent) : Repository<CredentialExchangeRecord>(CredentialExchangeRecord::class, agent) {
    suspend fun findByThreadAndConnectionId(threadId: String, connectionId: String?): CredentialExchangeRecord? {
        return if (connectionId != null) {
            findSingleByQuery("{\"threadId\": \"$threadId\", \"connectionId\": \"$connectionId\"}")
        } else {
            findSingleByQuery("{\"threadId\": \"$threadId\"}")
        }
    }

    suspend fun getByThreadAndConnectionId(threadId: String, connectionId: String?): CredentialExchangeRecord {
        return if (connectionId != null) {
            getSingleByQuery("{\"threadId\": \"$threadId\", \"connectionId\": \"$connectionId\"}")
        } else {
            getSingleByQuery("{\"threadId\": \"$threadId\"}")
        }
    }
}
