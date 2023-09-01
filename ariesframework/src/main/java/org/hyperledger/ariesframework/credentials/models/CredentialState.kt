package org.hyperledger.ariesframework.credentials.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class CredentialState {
    @SerialName("proposal-sent")
    ProposalSent,

    @SerialName("proposal-received")
    ProposalReceived,

    @SerialName("offer-sent")
    OfferSent,

    @SerialName("offer-received")
    OfferReceived,

    @SerialName("declined")
    Declined,

    @SerialName("request-sent")
    RequestSent,

    @SerialName("request-received")
    RequestReceived,

    @SerialName("credential-issued")
    CredentialIssued,

    @SerialName("credential-received")
    CredentialReceived,

    @SerialName("done")
    Done,
}
