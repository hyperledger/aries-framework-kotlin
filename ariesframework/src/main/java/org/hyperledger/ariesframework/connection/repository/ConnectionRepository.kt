package org.hyperledger.ariesframework.connection.repository

import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.storage.Repository

class ConnectionRepository(agent: Agent) : Repository<ConnectionRecord>(ConnectionRecord::class, agent)
