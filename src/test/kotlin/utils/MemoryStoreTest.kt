package utils

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MemoryStoreTest {
    private lateinit var store: MemoryStore

    @BeforeEach
    fun setUp() {
        store = MemoryStore(":memory:")
        store.init()
    }

    @AfterEach
    fun tearDown() {
        store.close()
    }

    @Test
    fun `init creates tables and does not throw`() {
        val s = MemoryStore(":memory:")
        assertDoesNotThrow { s.init() }
        s.close()
    }

    @Test
    fun `save and load recent messages`() {
        store.saveMessage("user", "hello")
        store.saveMessage("assistant", "hi there")

        val messages = store.loadRecentMessages(10)
        assertEquals(2, messages.size)
        assertEquals("user", messages[0].role)
        assertEquals("hello", messages[0].content)
        assertEquals("assistant", messages[1].role)
        assertEquals("hi there", messages[1].content)
    }

    @Test
    fun `loadRecentMessages respects limit`() {
        repeat(5) { store.saveMessage("user", "msg$it") }

        val limited = store.loadRecentMessages(2)
        assertEquals(2, limited.size)
    }

    @Test
    fun `loadRecentMessages returns all when fewer than limit`() {
        store.saveMessage("user", "only")
        val msgs = store.loadRecentMessages(10)
        assertEquals(1, msgs.size)
    }

    @Test
    fun `searchMessages finds matching content`() {
        store.saveMessage("user", "the apple is red")
        store.saveMessage("assistant", "the banana is yellow")
        store.saveMessage("user", "I like apples")

        val result = store.searchMessages("apple")
        assertTrue(result.contains("apple"))
        assertTrue(result.contains("red"))
        assertTrue(result.contains("apples"))
    }

    @Test
    fun `searchMessages returns not found for no matches`() {
        store.saveMessage("user", "hello world")
        val result = store.searchMessages("nonexistent")
        assertEquals("No messages matching 'nonexistent' found", result)
    }

    @Test
    fun `empty store returns empty results`() {
        assertTrue(store.loadRecentMessages(10).isEmpty())
        assertEquals("No messages matching 'x' found", store.searchMessages("x"))
    }

    @Test
    fun `close sets connection to null`() {
        store.close()
        assertDoesNotThrow { store.close() }
    }

    @Test
    fun `multiple stores are isolated`() {
        val store2 = MemoryStore(":memory:")
        store2.init()

        store.saveMessage("user", "only_in_first")
        assertTrue(store2.loadRecentMessages(10).isEmpty())

        store2.close()
    }

    @Test
    fun `save and load LTM entries`() {
        val id1 = store.saveLTMEntry("2026-06-15", "Session summary one", """{"type": "daily"}""")
        val id2 = store.saveLTMEntry("2026-06-15", "Session summary two")

        assertTrue(id1 > 0)
        assertTrue(id2 > 0)

        val entries = store.getLTMEntries("2026-06-15")
        assertEquals(2, entries.size)
        assertEquals("Session summary one", entries[0].content)
        assertEquals("Session summary two", entries[1].content)
    }

    @Test
    fun `LTM entries filtered by week`() {
        store.saveLTMEntry("2026-06-08", "Old week")
        store.saveLTMEntry("2026-06-15", "Current week")

        val current = store.getLTMEntries("2026-06-15")
        assertEquals(1, current.size)
        assertEquals("Current week", current[0].content)
    }

    @Test
    fun `getDistinctWeeks returns all weeks`() {
        store.saveLTMEntry("2026-06-01", "a")
        store.saveLTMEntry("2026-06-08", "b")
        store.saveLTMEntry("2026-06-15", "c")

        val weeks = store.getDistinctWeeks()
        assertEquals(3, weeks.size)
    }

    @Test
    fun `deleteLTMEntries removes entries for a week`() {
        store.saveLTMEntry("2026-06-08", "to delete")
        store.deleteLTMEntries("2026-06-08")

        assertTrue(store.getLTMEntries("2026-06-08").isEmpty())
    }

    @Test
    fun `save and load LTM summaries`() {
        val entryId = store.saveLTMEntry("2026-06-15", "Main content")

        val s1 = store.saveLTMSummary(entryId, "factual", "Factual summary")
        val s2 = store.saveLTMSummary(entryId, "preferences", "Pref summary")

        assertTrue(s1 > 0)
        assertTrue(s2 > 0)

        val summaries = store.getLTMSummaries(entryId)
        assertEquals(2, summaries.size)
        assertEquals("factual" to "Factual summary", summaries[0])
        assertEquals("preferences" to "Pref summary", summaries[1])
    }

    @Test
    fun `save and load archive entries`() {
        val bytes = MemoryStore.embeddingToBytes(floatArrayOf(0.1f, 0.2f, 0.3f))

        val id = store.saveToArchive(
            originalEntryId = 1L,
            weekStart = "2026-06-08",
            content = "Archived memory",
            perspective = "factual",
            semanticTags = "test, example",
            embedding = bytes,
            metadata = """{"source": "test"}"""
        )

        assertTrue(id > 0)

        val loaded = store.loadArchiveWithEmbeddings()
        assertEquals(1, loaded.size)
        assertEquals("Archived memory", loaded[0].third)
        assertEquals(3, MemoryStore.bytesToEmbedding(loaded[0].second).size)
    }

    @Test
    fun `embedding byte conversion roundtrip`() {
        val original = floatArrayOf(0.1f, -0.5f, 0.8f, 1.0f, -0.0f)
        val bytes = MemoryStore.embeddingToBytes(original)
        val restored = MemoryStore.bytesToEmbedding(bytes)

        assertEquals(original.size, restored.size)
        for (i in original.indices) {
            assertEquals(original[i], restored[i], 1e-6f)
        }
    }

    @Test
    fun `getArchiveEntry returns full entry`() {
        val bytes = MemoryStore.embeddingToBytes(floatArrayOf(0.5f))
        val id = store.saveToArchive(
            originalEntryId = null,
            weekStart = "2026-06-01",
            content = "test content",
            perspective = "goals",
            semanticTags = "goal, target",
            embedding = bytes
        )

        val entry = store.getArchiveEntry(id)
        assertTrue(entry != null)
        assertEquals("goals", entry!!.perspective)
        assertEquals("goal, target", entry.semanticTags)
        assertEquals("test content", entry.content)
        assertEquals("2026-06-01", entry.weekStart)
    }

    @Test
    fun `empty archive returns empty list`() {
        assertTrue(store.loadArchiveWithEmbeddings().isEmpty())
    }
}
