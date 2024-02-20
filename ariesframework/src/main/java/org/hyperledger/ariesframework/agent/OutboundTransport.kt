package org.hyperledger.ariesframework.agent

import okhttp3.OkHttpClient
import org.hyperledger.ariesframework.OutboundPackage

interface OutboundTransport {
    suspend fun sendPackage(_package: OutboundPackage)
}

object AgentHttpClient {
    val client = OkHttpClient().newBuilder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()
}
