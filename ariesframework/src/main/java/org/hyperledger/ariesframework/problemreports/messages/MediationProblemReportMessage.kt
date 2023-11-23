package org.hyperledger.ariesframework.problemreports.messages

import kotlinx.serialization.Serializable

@Serializable
class MediationProblemReportMessage() : BaseProblemReportMessage() {
    companion object {
        const val type = "https://didcomm.org/coordinate-mediation/1.0/problem-report"
    }
}
