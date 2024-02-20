package org.hyperledger.ariesframework.anoncreds.storage

import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.storage.Repository

class RevocationRegistryRepository(agent: Agent) : Repository<RevocationRegistryRecord>(
    RevocationRegistryRecord::class,
    agent,
) {
    suspend fun findByCredDefId(credDefId: String): RevocationRegistryRecord? {
        return findSingleByQuery("{\"credDefId\": \"$credDefId\"}")
    }

    // We don't need lock here because this is for testing only.
    suspend fun incrementRegistryIndex(credDefId: String): Int {
        val record = getSingleByQuery("{\"credDefId\": \"$credDefId\"}")
        record.registryIndex += 1
        update(record)
        return record.registryIndex
    }
}
