package org.hyperledger.ariesframework.agent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
) : AgentMessage(generateId(), BaseProblemReportMessage.type) {
    constructor() : this(DescriptionOptions("", ""), null) {
        throw Exception("Creating a ProblemReportMessage is not supported")
    }
    companion object {
        const val type = "https://didcomm.org/notification/1.0/problem-report"
    }
}
