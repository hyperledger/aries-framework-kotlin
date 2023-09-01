package org.hyperledger.ariesframework.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DIDParserTest {
    val VERKEY = "8HH5gYEeNc3z7PYXmd54d4x6qAfCNrqQqEB3nS7Zfu7K"
    val DERIVED_DID_KEY = "did:key:z6MkmjY8GnV5i9YTDtPETC2uUAW6ejw3nk5mXF5yci5ab7th"
    val VALID_SECP256K1_0 = "did:key:zQ3shokFTS3brHcDQrn82RUDfCZESWL1ZdCEJwekUDPQiYBme"

    @Test
    fun getMethodTest() {
        val did = "did:key:12345"
        assertEquals(DIDParser.getMethod(did), "key")
    }

    @Test
    fun getMethodIdTest() {
        var did = "did:aries:did.example.com"
        assertEquals(DIDParser.getMethodId(did), "did.example.com")

        did = "did:example:123456/path"
        assertEquals(DIDParser.getMethodId(did), "123456")

        did = "did:example:123456?versionId=1"
        assertEquals(DIDParser.getMethodId(did), "123456")

        did = "did:example:123?service=agent&relativeRef=/credentials#degree"
        assertEquals(DIDParser.getMethodId(did), "123")
    }

    @Test
    fun didKeyEncodingTest() {
        var did = DIDParser.convertVerkeyToDidKey(VERKEY)
        assertEquals(did, DERIVED_DID_KEY)

        var verkey = DIDParser.convertDidKeyToVerkey(did)
        assertEquals(verkey, VERKEY)

        var verkey2 = DIDParser.convertFingerprintToVerkey(DIDParser.getMethodId(did))
        assertEquals(verkey2, VERKEY)

        try {
            DIDParser.convertDidKeyToVerkey(VALID_SECP256K1_0)
            assert(false)
        } catch (e: Exception) {
            // expected
        }
    }
}
