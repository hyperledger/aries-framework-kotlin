package org.hyperledger.ariesframework.routing.messages

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.BaseProblemReportMessage

@Serializable
class ProblemReportMessage() : BaseProblemReportMessage() {
    companion object {
        const val type = "https://didcomm.org/coordinate-mediation/1.0/problem-report"
    }
}
