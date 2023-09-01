package org.hyperledger.ariesframework.connection.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.connection.models.didauth.DidDoc

@Serializable
enum class ConnectionRole {
    @SerialName("inviter")
    Inviter,

    @SerialName("invitee")
    Invitee,
}

@Serializable
enum class ConnectionState {
    @SerialName("invited")
    Invited,

    @SerialName("requested")
    Requested,

    @SerialName("responded")
    Responded,

    @SerialName("complete")
    Complete,
}

@Serializable
class Connection(
    @SerialName("DID")
    val did: String,
    @SerialName("DIDDoc")
    val didDoc: DidDoc?,
)
