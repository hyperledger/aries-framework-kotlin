package org.hyperledger.ariesframework.credentials.messages
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.BaseProblemReportMessage
import org.hyperledger.ariesframework.agent.DescriptionOptions
import org.hyperledger.ariesframework.agent.decorators.ThreadDecorator

@Serializable
class CredentialProblemReportMessage private constructor() : BaseProblemReportMessage(
    // See: https://github.com/hyperledger/aries-cloudagent-python/blob/main/aries_cloudagent/protocols/issue_credential/v1_0/messages/credential_problem_report.py // ktlint-disable max-line-length
    DescriptionOptions(
        "Issuance abandoned",
        "issuance-abandoned",
    ),
    null,
) {
    constructor(threadId: String) : this() {
        thread = ThreadDecorator(threadId)
        type = CredentialProblemReportMessage.type
    }
    companion object {
        const val type = "https://didcomm.org/issue-credential/1.0/problem-report"
    }
}
