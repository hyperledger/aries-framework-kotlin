package org.hyperledger.ariesframework.agent.decorators

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ThreadDecorator(
    @SerialName("thid")
    var threadId: String? = null,
    @SerialName("pthid")
    var parentThreadId: String? = null,
    @SerialName("sender_order")
    val senderOrder: Int? = null,
    @SerialName("received_orders")
    val receivedOrders: Map<String, Int>? = null,
)
