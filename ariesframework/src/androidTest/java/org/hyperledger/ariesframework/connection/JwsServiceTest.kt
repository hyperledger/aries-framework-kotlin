package org.hyperledger.ariesframework.connection

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.hyperledger.ariesframework.TestHelper
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.decorators.JwsFlattenedFormat
import org.hyperledger.ariesframework.decodeBase64url
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class JwsServiceTest {
    lateinit var agent: Agent
    val seed = "00000000000000000000000000000My2"
    val verkey = "kqa2HyagzfMAq42H5f9u3UMwnSBPQx2QfrSyXbUPxMn"
    val payload = "hello".toByteArray()

    @Before
    fun setUp() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = TestHelper.getBaseConfig()
        agent = Agent(context, config)
        agent.initialize()

        val didInfo = agent.wallet.createDid(seed)
        Assert.assertEquals(didInfo.verkey, verkey)
    }

    @After
    fun tearDown() = runTest {
        agent.reset()
    }

    @Test
    fun testCreateAndVerify() = runTest {
        val jws = agent.jwsService.createJws(payload, verkey)
        Assert.assertEquals(
            "did:key:z6MkfD6ccYE22Y9pHKtixeczk92MmMi2oJCP6gmNooZVKB9A",
            jws.header?.get("kid"),
        )
        val protectedJson = jws.protected.decodeBase64url().decodeToString()
        val protected = Json.decodeFromString<JsonObject>(protectedJson)
        Assert.assertEquals("EdDSA", protected["alg"]?.jsonPrimitive?.content)
        Assert.assertNotNull(protected["jwk"])

        val (valid, signer) = agent.jwsService.verifyJws(jws, payload)
        Assert.assertTrue(valid)
        Assert.assertEquals(signer, verkey)
    }

    @Test
    fun testFlattenedJws() = runTest {
        val jws = agent.jwsService.createJws(payload, verkey)
        val list = JwsFlattenedFormat(arrayListOf(jws))

        val (valid, signer) = agent.jwsService.verifyJws(list, payload)
        Assert.assertTrue(valid)
        Assert.assertEquals(signer, verkey)
    }

    @Test
    fun testVerifyFail() = runTest {
        val wrongPayload = "world".toByteArray()
        val jws = agent.jwsService.createJws(payload, verkey)
        val (valid, signer) = agent.jwsService.verifyJws(jws, wrongPayload)
        Assert.assertFalse(valid)
        Assert.assertEquals(signer, verkey)
    }
}
