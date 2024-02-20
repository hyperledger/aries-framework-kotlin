package org.hyperledger.ariesframework.ledger

import kotlinx.serialization.Serializable

@Serializable
data class IndyResponse(val op: String, val reason: String? = null)

@Serializable
data class BaseResponse<T>(val op: String, val result: BaseResult<T>)

@Serializable
data class BaseResult<T>(val type: String, val seqNo: Int? = null, val data: T)

typealias SchemaResponse = BaseResponse<SchemaData>

@Serializable
data class SchemaData(val attr_names: List<String>? = null, val name: String, val version: String)

typealias RevRegDefResponse = BaseResponse<RevRegDefData>

@Serializable
data class RevRegDefData(
    val id: String,
    val credDefId: String,
    val tag: String,
    val values: RevRegDefValues,
)

@Serializable
data class RevRegDefValues(
    val issuanceType: String,
    val maxCredNum: Int,
    val tailsHash: String,
    val tailsLocation: String,
    val publicKeys: RevRegDefPublicKeys,
)

@Serializable
data class RevRegDefPublicKeys(val accumKey: AccumKey)

@Serializable
data class AccumKey(val z: String)

typealias RegRegResponse = BaseResponse<RegRegData>

@Serializable
data class RegRegData(val seqNo: Int, val value: AccumValue, val txnTime: Int)

@Serializable
data class AccumValue(val accum: String)

typealias RevRegDeltaResponse = BaseResponse<RevRegDeltaData>

@Serializable
data class RevRegDeltaData(val value: RevRegDeltaValue)

@Serializable
data class RevRegDeltaValue(
    val accum_from: RegRegData? = null,
    val accum_to: RegRegData,
    val issued: List<Int>,
    val revoked: List<Int>,
)
