package org.hyperledger.ariesframework.agent

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class EventBusTest {
    @Test
    fun testSubscribe() = runBlocking {
        val eventBus = EventBus()
        var count = 0
        eventBus.subscribe<String> { assertEquals("hello", it) }
        eventBus.subscribe<String> { count++ }
        eventBus.subscribe<String> { count++ }

        delay(0.1.seconds) // Wait for the subscribers to be registered
        eventBus.publish("hello")
        delay(0.1.seconds) // Wait for the subscribers to be notified
        assertEquals(2, count)
    }

    @Test
    fun testEventValues() = runTest {
        val eventBus = EventBus()
        val values = mutableListOf<Any>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            eventBus.events.toList(values)
        }

        for (i in 1..10) {
            eventBus.publish(i)
        }
        assertEquals(10, values.size)
        assertEquals(1, values[0])
        assertEquals(10, values[9])
    }

    @Test
    fun testWaitForEvent() = runTest {
        val eventBus = EventBus()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            delay(1.seconds)
            eventBus.publish("hello")
            delay(30.seconds)
            eventBus.publish("world")
        }
        assertTrue(eventBus.waitFor<String> { it == "hello" })
        // It should timeout and return false
        assertFalse(eventBus.waitFor<String> { it == "world" })
    }
}
