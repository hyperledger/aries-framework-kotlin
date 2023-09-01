package org.hyperledger.ariesframework.routing.repository

import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.storage.Repository

class MediationRepository(agent: Agent) : Repository<MediationRecord>(MediationRecord::class, agent) {
    suspend fun getByConnectionId(connectionId: String): MediationRecord {
        return getSingleByQuery("{\"connectionId\": \"$connectionId\"}")
    }

    suspend fun getDefault(): MediationRecord? {
        return findSingleByQuery("{}")
    }
}
