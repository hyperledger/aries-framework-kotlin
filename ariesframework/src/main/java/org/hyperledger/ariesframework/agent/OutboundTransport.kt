package org.hyperledger.ariesframework.agent

import okhttp3.OkHttpClient
import org.hyperledger.ariesframework.OutboundPackage

interface OutboundTransport {
    suspend fun sendPackage(_package: OutboundPackage)
}

object AgentHttpClient {
    val client = OkHttpClient()
}
