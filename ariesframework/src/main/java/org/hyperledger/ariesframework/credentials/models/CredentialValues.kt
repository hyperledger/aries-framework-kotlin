package org.hyperledger.ariesframework.credentials.models

import org.hyperledger.ariesframework.toJsonString
import java.math.BigInteger
import java.security.MessageDigest

object CredentialValues {
    private val SHA256: MessageDigest = MessageDigest.getInstance("SHA-256")

    fun convertAttributesToValues(attributes: List<CredentialPreviewAttribute>): String {
        val values = attributes.fold("") { result, attribute ->
            result + "\"${attribute.name}\": ${mapOf("raw" to attribute.value, "encoded" to encode(attribute.value)).toJsonString()},"
        }

        return "{ ${values.dropLast(1)} }"
    }

    fun encode(value: String): String {
        return if (isInt32(value)) {
            value
        } else {
            val sha256 = SHA256.digest(value.encodeToByteArray())
            BigInteger(1, sha256).toString()
        }
    }

    fun isInt32(value: String): Boolean {
        return value.toIntOrNull() != null
    }

    fun checkValidEncoding(raw: String, encoded: String): Boolean {
        return encoded == encode(raw)
    }
}
