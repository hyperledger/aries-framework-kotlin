package org.hyperledger.ariesframework.connection.models.didauth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("did-communication")
open class DidCommService(
    override val id: String,
    override val serviceEndpoint: String,
    override val recipientKeys: List<String>,
    override val routingKeys: List<String>? = null,
    override val priority: Int? = 0,
    override val accept: List<String>? = null,
) : DidDocService(), DidComm
