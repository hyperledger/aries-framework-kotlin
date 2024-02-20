package org.hyperledger.ariesframework.storage

import androidx.test.platform.app.InstrumentationRegistry
import askar_uniffi.ErrorCode
import kotlinx.coroutines.test.runTest
import org.hyperledger.ariesframework.Tags
import org.hyperledger.ariesframework.TestHelper
import org.hyperledger.ariesframework.agent.Agent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class RepositoryTest {
    lateinit var agent: Agent
    lateinit var repository: Repository<TestRecord>

    @Before
    fun setUp() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = TestHelper.getBaseConfig()
        agent = Agent(context, config)
        agent.wallet.initialize()
        repository = Repository<TestRecord>(agent)
    }

    @After
    fun tearDown() = runTest {
        agent.reset()
    }

    private suspend fun insertRecord(id: String = BaseRecord.generateId(), tags: Tags? = null): TestRecord {
        val record = TestRecord(id = id, _tags = tags ?: mapOf("myTag" to "foobar"), foo = "bar")
        repository.save(record)
        return record
    }

    @Test
    fun testSave() = runTest {
        val record = insertRecord(id = "test-id")
        try {
            repository.save(record)
            assert(false) { "Duplicate error expected" }
        } catch (e: ErrorCode.Duplicate) {
            // expected
        } catch (e: Exception) {
            assert(false) { "Unexpected exception: $e" }
        }
    }

    @Test
    fun testSaveAndGet() = runTest {
        val record = insertRecord()
        val record2 = repository.getById(record.id)
        assertEquals(record.id, record2.id)
        assertEquals(record.createdAt, record2.createdAt)
        assertEquals(record.getTags(), record2.getTags())
        assertEquals(record.foo, record2.foo)

        try {
            repository.getById("not-found")
            assert(false) { "NotFound error expected" }
        } catch (e: ErrorCode.NotFound) {
            // expected
        } catch (e: Exception) {
            assert(false) { "Unexpected exception: $e" }
        }
    }

    @Test
    fun testUpdate() = runTest {
        var record = TestRecord(id = "test-id", _tags = mapOf("myTag" to "foobar"), foo = "test")
        try {
            repository.update(record)
            assert(false) { "NotFound error expected" }
        } catch (e: ErrorCode.NotFound) {
            // expected
        } catch (e: Exception) {
            assert(false) { "Unexpected exception: $e" }
        }

        repository.save(record)
        var tags = record.getTags().toMutableMap()
        tags["foo"] = "bar"
        record.setTags(tags)
        record.foo = "baz"
        repository.update(record)
        val record2 = repository.getById(record.id)

        assertEquals(record.getTags(), record2.getTags())
        assertEquals(record.foo, record2.foo)
    }

    @Test
    fun testDelete() = runTest {
        val record = insertRecord()
        repository.delete(record)
        try {
            repository.getById(record.id)
            assert(false) { "NotFound error expected" }
        } catch (e: ErrorCode.NotFound) {
            // expected
        } catch (e: Exception) {
            assert(false) { "Unexpected exception: $e" }
        }
    }

    @Test
    fun testGetAll() = runTest {
        val record1 = insertRecord()
        val record2 = insertRecord()
        val records = repository.getAll()
        assertEquals(2, records.size)
        assert(records.any { it.id == record1.id })
        assert(records.any { it.id == record2.id })
    }

    @Test
    fun testFindByQuery() = runTest {
        val expectedRecord = insertRecord(tags = mapOf("myTag" to "foobar"))
        insertRecord(tags = mapOf("myTag" to "notfoobar"))
        val records = repository.findByQuery("{\"myTag\": \"foobar\"}")
        assertEquals(1, records.size)
        assertEquals(records[0].id, expectedRecord.id)

        val emptyRecords = repository.findByQuery("{\"myTag\": \"notfound\"}")
        assert(emptyRecords.isEmpty())
    }

    @Test
    fun testFindById() = runTest {
        val record = insertRecord()
        val record2 = repository.findById(record.id)!!
        assertEquals(record.id, record2.id)
        assertEquals(record.createdAt, record2.createdAt)
        assertEquals(record.getTags(), record2.getTags())
        assertEquals(record.foo, record2.foo)

        try {
            val record3 = repository.findById("not-found")
            assertNull(record3)
        } catch (e: Exception) {
            assert(false) { "findById() should not throw" }
        }
    }

    @Test
    fun testFindSingByQuery() = runTest {
        val expectedRecord = insertRecord(tags = mapOf("myTag" to "foobar"))
        val record = repository.findSingleByQuery("{\"myTag\": \"foobar\"}")
        assertEquals(record?.id, expectedRecord.id)
        val record2 = repository.findSingleByQuery("{\"myTag\": \"notfound\"}")
        assertNull(record2)

        insertRecord(tags = mapOf("myTag" to "foobar")) // Insert duplicate tags
        try {
            repository.findSingleByQuery("{\"myTag\": \"foobar\"}")
            assert(false) { "Should throw error when more than one record found" }
        } catch (e: ErrorCode.Duplicate) {
            // expected
        } catch (e: Exception) {
            assert(false) { "Unexpected exception: $e" }
        }
    }

    @Test
    fun testGetSingleByQuery() = runTest {
        val expectedRecord = insertRecord(tags = mapOf("myTag" to "foobar"))
        val record = repository.getSingleByQuery("{\"myTag\": \"foobar\"}")
        assertEquals(record?.id, expectedRecord.id)

        try {
            repository.getSingleByQuery("{\"myTag\": \"notfound\"}")
            assert(false) { "Should throw error when not found" }
        } catch (e: ErrorCode.NotFound) {
            // expected
        } catch (e: Exception) {
            assert(false) { "Unexpected exception: $e" }
        }
    }
}
