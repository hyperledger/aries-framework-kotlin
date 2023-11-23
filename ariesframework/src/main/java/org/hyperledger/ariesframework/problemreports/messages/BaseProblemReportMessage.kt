package org.hyperledger.ariesframework.problemreports.messages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.AgentMessage

@Serializable
data class DescriptionOptions(
    val en: String,
    val code: String,
)

@Serializable
data class FixHintOptions(
    val en: String,
)

@Serializable
open class BaseProblemReportMessage(
    open val description: DescriptionOptions,
    @SerialName("fix_hint")
    open val fixHint: FixHintOptions? = null,
) : AgentMessage(generateId(), type) {
    constructor() : this(DescriptionOptions("", ""), null) {
        throw Exception("Creating a ProblemReportMessage is not supported")
    }
    companion object {
        const val type = "https://didcomm.org/notification/1.0/problem-report"
    }
}
