package org.hyperledger.ariesframework.proofs.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ProofState {
    @SerialName("proposal-sent")
    ProposalSent,

    @SerialName("proposal-received")
    ProposalReceived,

    @SerialName("request-sent")
    RequestSent,

    @SerialName("request-received")
    RequestReceived,

    @SerialName("presentation-sent")
    PresentationSent,

    @SerialName("presentation-received")
    PresentationReceived,

    @SerialName("declined")
    Declined,

    @SerialName("done")
    Done,
}
