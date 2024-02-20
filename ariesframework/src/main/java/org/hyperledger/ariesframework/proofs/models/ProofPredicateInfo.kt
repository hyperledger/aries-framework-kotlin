package org.hyperledger.ariesframework.proofs.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PredicateType {
    @SerialName("<")
    LessThan,

    @SerialName("<=")
    LessThanOrEqualTo,

    @SerialName(">")
    GreaterThan,

    @SerialName(">=")
    GreaterThanOrEqualTo,
}

@Serializable
data class ProofPredicateInfo(
    val name: String,
    @SerialName("non_revoked")
    val nonRevoked: RevocationInterval? = null,
    @SerialName("p_type")
    val predicateType: PredicateType,
    @SerialName("p_value")
    val predicateValue: Int,
    val restrictions: List<AttributeFilter>? = null,
) {
    fun asProofAttributeInfo(): ProofAttributeInfo {
        return ProofAttributeInfo(name, null, nonRevoked, restrictions)
    }
}
