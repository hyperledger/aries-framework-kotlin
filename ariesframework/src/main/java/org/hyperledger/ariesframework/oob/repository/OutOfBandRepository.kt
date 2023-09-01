package org.hyperledger.ariesframework.oob.repository

import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.storage.Repository

class OutOfBandRepository(agent: Agent) : Repository<OutOfBandRecord>(OutOfBandRecord::class, agent) {
    suspend fun findByInvitationId(invitationId: String): OutOfBandRecord? {
        return findSingleByQuery("{\"invitationId\": \"$invitationId\"}")
    }

    suspend fun findAllByInvitationKey(invitationKey: String): List<OutOfBandRecord> {
        return findByQuery("{\"invitationKey\": \"$invitationKey\"}")
    }

    suspend fun findByFingerprint(fingerprint: String): OutOfBandRecord? {
        return findSingleByQuery("{\"recipientKeyFingerprints\": [\"$fingerprint\"]}")
    }
}
