package org.hyperledger.ariesframework.oob.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.AgentMessage
import org.hyperledger.ariesframework.routing.Routing

enum class InvitationType {
    Connection,
    OOB,
    Unknown,
}

@Serializable
enum class OutOfBandRole {
    @SerialName("sender")
    Sender,

    @SerialName("receiver")
    Receiver,
}

@Serializable
enum class OutOfBandState {
    @SerialName("initial")
    Initial,

    @SerialName("await-response")
    AwaitResponse,

    @SerialName("prepare-response")
    PrepareResponse,

    @SerialName("done")
    Done,
}

data class CreateOutOfBandInvitationConfig(
    val label: String? = null,
    val alias: String? = null,
    val imageUrl: String? = null,
    val goalCode: String? = null,
    val goal: String? = null,
    val handshake: Boolean? = null,
    val messages: List<AgentMessage>? = null,
    val multiUseInvitation: Boolean? = null,
    val autoAcceptConnection: Boolean? = null,
    val routing: Routing? = null,
)

data class ReceiveOutOfBandInvitationConfig(
    val label: String? = null,
    val alias: String? = null,
    val imageUrl: String? = null,
    val autoAcceptInvitation: Boolean? = null,
    val autoAcceptConnection: Boolean? = null,
    val reuseConnection: Boolean? = null,
    val routing: Routing? = null,
)
