package org.hyperledger.ariesframework.util

object DIDParser {
    val PCT_ENCODED = "(?:%[0-9a-fA-F]{2})"
    val ID_CHAR = "(?:[a-zA-Z0-9._-]|$PCT_ENCODED)"
    val METHOD = "([a-z0-9]+)"
    val METHOD_ID = "((?:$ID_CHAR*:)*($ID_CHAR+))"
    val PARAM_CHAR = "[a-zA-Z0-9_.:%-]"
    val PARAM = ";$PARAM_CHAR=$PARAM_CHAR*"
    val PARAMS = "(($PARAM)*)"
    val PATH = "(/[^#?]*)?"
    val QUERY = "([?][^#]*)?"
    val FRAGMENT = "(#.*)?"
    val DID_URL = "^did:$METHOD:$METHOD_ID$PARAMS$PATH$QUERY$FRAGMENT$"

    val MULTICODEC_PREFIX_ED25519 = byteArrayOf(0xed.toByte(), 0x01)
    val DIDKEY_PREFIX = "did:key"
    val BASE58_PREFIX = "z"

    fun getMethod(did: String): String {
        val matchResult = Regex(DID_URL).find(input = did)
        if (matchResult?.groupValues == null) {
            throw java.lang.Exception()
        }

        return matchResult.groupValues[1]
    }

    fun getMethodId(did: String): String {
        val matchResult = Regex(DID_URL).find(input = did)
        if (matchResult?.groupValues == null) {
            throw java.lang.Exception()
        }

        return matchResult.groupValues[2]
    }

    fun convertVerkeysToDidKeys(verkeys: List<String>): List<String> {
        return verkeys.map { verkey ->
            convertVerkeyToDidKey(verkey)
        }
    }

    fun convertDidKeysToVerkeys(didKeys: List<String>): List<String> {
        return didKeys.map { didKey ->
            convertDidKeyToVerkey(didKey)
        }
    }

    fun convertVerkeyToDidKey(verkey: String): String {
        val bytes = try {
            Base58.decode(verkey)
        } catch (e: Exception) {
            throw Exception("Invalid base58 encoded verkey: $verkey")
        }
        val bytesWithPrefix = MULTICODEC_PREFIX_ED25519 + bytes
        val base58PublicKey = Base58.encode(bytesWithPrefix)
        return "$DIDKEY_PREFIX:$BASE58_PREFIX$base58PublicKey"
    }

    fun convertDidKeyToVerkey(did: String): String {
        val method = getMethod(did)
        if (method != "key") {
            throw Exception("Invalid DID method: $method")
        }

        val methodId = getMethodId(did)
        return convertFingerprintToVerkey(methodId)
    }

    fun convertFingerprintToVerkey(fingerprint: String): String {
        val base58PublicKey = fingerprint.drop(1)
        val bytes = try {
            Base58.decode(base58PublicKey)
        } catch (e: Exception) {
            throw Exception("Invalid base58 encoded fingerprint: $fingerprint")
        }
        val codec = bytes.take(2).toByteArray()
        if (!codec.contentEquals(MULTICODEC_PREFIX_ED25519)) {
            throw Exception("Invalid DID key codec: ${codec[0]}, ${codec[1]}")
        }

        val verkey = bytes.drop(2)
        return Base58.encode(verkey.toByteArray())
    }
}
