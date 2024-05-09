package org.hyperledger.ariesframework.connection.models.didauth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("DIDCommMessaging")
open class DidCommV2Service(
    override val id: String,
    val serviceEndpoint: ServiceEndpoint,
) : DidDocService()

@Serializable
class ServiceEndpoint(
    val uri: String,
    val routingKeys: List<String>? = null,
    val accept: List<String>? = null,
)
