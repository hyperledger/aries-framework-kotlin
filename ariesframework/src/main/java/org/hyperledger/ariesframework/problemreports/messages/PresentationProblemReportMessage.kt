package org.hyperledger.ariesframework.problemreports.messages

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.decorators.ThreadDecorator

@Serializable
class PresentationProblemReportMessage private constructor() : BaseProblemReportMessage(
    // See: https://github.com/hyperledger/aries-cloudagent-python/blob/main/aries_cloudagent/protocols/present_proof/v1_0/messages/presentation_problem_report.py // ktlint-disable max-line-length
    DescriptionOptions(
        "Proof abandoned",
        "abandoned",
    ),
    null,
) {
    constructor(threadId: String) : this() {
        thread = ThreadDecorator(threadId)
        type = Companion.type
    }
    companion object {
        const val type = "https://didcomm.org/present-proof/1.0/problem-report"
    }
}
