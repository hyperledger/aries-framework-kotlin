package org.hyperledger.ariesframework.oob.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class HandshakeProtocol {
    @SerialName("https://didcomm.org/connections/1.0")
    Connections,

    @SerialName("https://didcomm.org/didexchange/1.0")
    DidExchange10,

    @SerialName("https://didcomm.org/didexchange/1.1")
    DidExchange11,
}
