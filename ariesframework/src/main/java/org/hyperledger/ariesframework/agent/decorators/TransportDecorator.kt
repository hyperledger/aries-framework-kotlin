package org.hyperledger.ariesframework.agent.decorators

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class TransportDecorator(
    @SerialName("return_route")
    val returnRoute: String? = null,
    @SerialName("return_route_thread")
    val returnRouteThread: String? = null,
)
