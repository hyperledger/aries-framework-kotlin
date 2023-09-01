package org.hyperledger.ariesframework.storage

import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.AgentMessage
import org.hyperledger.ariesframework.agent.Dispatcher

class DidCommMessageRepository(agent: Agent) : Repository<DidCommMessageRecord>(DidCommMessageRecord::class, agent) {
    override suspend fun save(record: DidCommMessageRecord) {
        throw Exception("Do not call save() directly. We need to change the prefix of the message type before save the record.")
    }

    suspend fun saveAgentMessage(role: DidCommMessageRole, agentMessage: AgentMessage, associatedRecordId: String) {
        if (agent.agentConfig.useLegacyDidSovPrefix) {
            agentMessage.replaceNewDidCommPrefixWithLegacyDidSov()
        }
        super.save(DidCommMessageRecord(agentMessage, role, associatedRecordId))
    }

    suspend fun saveOrUpdateAgentMessage(role: DidCommMessageRole, agentMessage: AgentMessage, associatedRecordId: String) {
        if (agent.agentConfig.useLegacyDidSovPrefix) {
            agentMessage.replaceNewDidCommPrefixWithLegacyDidSov()
        }
        val record = findSingleByQuery("{\"associatedRecordId\": \"$associatedRecordId\", \"messageType\": \"${agentMessage.type}\"}")
        if (record != null) {
            record.message = agentMessage.toJsonString()
            record.role = role
            update(record)
            return
        }
        saveAgentMessage(role, agentMessage, associatedRecordId)
    }

    suspend fun getAgentMessage(associatedRecordId: String, messageType: String): String {
        var type = messageType
        if (agent.agentConfig.useLegacyDidSovPrefix) {
            type = Dispatcher.replaceNewDidCommPrefixWithLegacyDidSov(messageType)
        }
        val record = getSingleByQuery("{\"associatedRecordId\": \"$associatedRecordId\", \"messageType\": \"$type\"}")
        return record.message
    }

    suspend fun findAgentMessage(associatedRecordId: String, messageType: String): String? {
        var type = messageType
        if (agent.agentConfig.useLegacyDidSovPrefix) {
            type = Dispatcher.replaceNewDidCommPrefixWithLegacyDidSov(messageType)
        }
        val record = findSingleByQuery("{\"associatedRecordId\": \"$associatedRecordId\", \"messageType\": \"$type\"}")
        return record?.message
    }
}
