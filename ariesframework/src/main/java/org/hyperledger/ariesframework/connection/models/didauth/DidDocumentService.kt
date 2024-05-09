package org.hyperledger.ariesframework.connection.models.didauth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("DidDocumentService")
class DidDocumentService(
    override val id: String,
    val serviceEndpoint: String,
) : DidDocService()
