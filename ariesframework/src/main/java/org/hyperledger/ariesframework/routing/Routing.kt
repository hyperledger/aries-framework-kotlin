package org.hyperledger.ariesframework.routing

data class Routing(
    val endpoints: List<String>,
    val verkey: String,
    val did: String,
    val routingKeys: List<String>,
    val mediatorId: String?,
)
