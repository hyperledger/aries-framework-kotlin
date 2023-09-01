package org.hyperledger.ariesframework.credentials

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.hyperledger.ariesframework.credentials.models.CredentialPreviewAttribute
import org.hyperledger.ariesframework.credentials.models.CredentialValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialValuesTest {
    private val testEncodings = mapOf(
        "address2" to mapOf(
            "raw" to "101 Wilson Lane",
            "encoded" to "68086943237164982734333428280784300550565381723532936263016368251445461241953",
        ),
        "zip" to mapOf(
            "raw" to "87121",
            "encoded" to "87121",
        ),
        "city" to mapOf(
            "raw" to "SLC",
            "encoded" to "101327353979588246869873249766058188995681113722618593621043638294296500696424",
        ),
        "address1" to mapOf(
            "raw" to "101 Tela Lane",
            "encoded" to "63690509275174663089934667471948380740244018358024875547775652380902762701972",
        ),
        "state" to mapOf(
            "raw" to "UT",
            "encoded" to "93856629670657830351991220989031130499313559332549427637940645777813964461231",
        ),
        "Empty" to mapOf(
            "raw" to "",
            "encoded" to "102987336249554097029535212322581322789799900648198034993379397001115665086549",
        ),
        "str True" to mapOf(
            "raw" to "True",
            "encoded" to "27471875274925838976481193902417661171675582237244292940724984695988062543640",
        ),
        "str False" to mapOf(
            "raw" to "False",
            "encoded" to "43710460381310391454089928988014746602980337898724813422905404670995938820350",
        ),
        "max i32" to mapOf(
            "raw" to "2147483647",
            "encoded" to "2147483647",
        ),
        "max i32 + 1" to mapOf(
            "raw" to "2147483648",
            "encoded" to "26221484005389514539852548961319751347124425277437769688639924217837557266135",
        ),
        "min i32" to mapOf(
            "raw" to "-2147483648",
            "encoded" to "-2147483648",
        ),
        "min i32 - 1" to mapOf(
            "raw" to "-2147483649",
            "encoded" to "68956915425095939579909400566452872085353864667122112803508671228696852865689",
        ),
        "str 0.0" to mapOf(
            "raw" to "0.0",
            "encoded" to "62838607218564353630028473473939957328943626306458686867332534889076311281879",
        ),
        "chr 0" to mapOf(
            "raw" to String(charArrayOf(0.toChar())),
            "encoded" to "49846369543417741186729467304575255505141344055555831574636310663216789168157",
        ),
        "chr 1" to mapOf(
            "raw" to String(charArrayOf(1.toChar())),
            "encoded" to "34356466678672179216206944866734405838331831190171667647615530531663699592602",
        ),
        "chr 2" to mapOf(
            "raw" to String(charArrayOf(2.toChar())),
            "encoded" to "99398763056634537812744552006896172984671876672520535998211840060697129507206",
        ),
    )

    @Test
    fun testEncoding() {
        for ((key, value) in testEncodings) {
            assertTrue("$key failed to encode correctly", CredentialValues.checkValidEncoding(value["raw"]!!, value["encoded"]!!))
        }
    }

    @Test
    fun testConvertAttributes() {
        val attributes = listOf(
            CredentialPreviewAttribute(name = "address2", value = "101 Wilson Lane"),
            CredentialPreviewAttribute(name = "zip", value = "87121"),
        )
        val values = CredentialValues.convertAttributesToValues(attributes)
        val credValues = Json.decodeFromString<JsonObject>(values)
        val address2 = credValues["address2"] as JsonObject
        assertEquals(address2["raw"]?.jsonPrimitive?.content, "101 Wilson Lane")
        assertEquals(
            address2["encoded"]?.jsonPrimitive?.content,
            "68086943237164982734333428280784300550565381723532936263016368251445461241953",
        )
        val zip = credValues["zip"] as JsonObject
        assertEquals(zip["raw"]?.jsonPrimitive?.content, "87121")
        assertEquals(zip["encoded"]?.jsonPrimitive?.content, "87121")
    }
}
