package org.hyperledger.ariesframework.credentials

import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import anoncreds_uniffi.CredentialDefinition
import kotlinx.coroutines.test.runTest
import org.hyperledger.ariesframework.TestHelper
import org.hyperledger.ariesframework.agent.Agent
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class LedgerServiceTest {
    lateinit var agent: Agent

    @Before
    fun setUp() = runTest(timeout = 30.seconds) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = TestHelper.getBaseConfig("faber", true)
        agent = Agent(context, config)
        agent.initialize()
    }

    @After
    fun tearDown() = runTest {
        agent.reset()
    }

    @Test @LargeTest
    fun testPrepareIssuance() = runTest {
        val attributes = listOf("name", "age")
        val credDefId = TestHelper.prepareForIssuance(agent, attributes)
        println("credential definition id: $credDefId")

        val credDefJson = agent.ledgerService.getCredentialDefinition(credDefId)
        val credDef = CredentialDefinition(credDefJson)
        println("schema id: ${credDef.schemaId()}")
        println("cred def id: ${credDef.credDefId()}")
    }
}
