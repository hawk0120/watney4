package utils

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
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
    fun `save and load memories`() {
        store.saveMemory("user_name", "Brady")
        store.saveMemory("favorite_color", "blue")

        val memories = store.loadMemories()
        assertEquals(2, memories.size)

        val name = memories.find { it.key == "user_name" }
        assertNotNull(name)
        assertEquals("Brady", name!!.content)
    }

    @Test
    fun `saveMemory upserts on duplicate key`() {
        store.saveMemory("key1", "original")
        store.saveMemory("key1", "updated")

        val memories = store.loadMemories()
        assertEquals(1, memories.count { it.key == "key1" })
        assertEquals("updated", memories.find { it.key == "key1" }?.content)
    }

    @Test
    fun `deleteMemory returns true and removes entry`() {
        store.saveMemory("temp", "to delete")
        val deleted = store.deleteMemory("temp")
        assertTrue(deleted)
        assertTrue(store.loadMemories().isEmpty())
    }

    @Test
    fun `deleteMemory returns false for non-existent key`() {
        val deleted = store.deleteMemory("does_not_exist")
        assertFalse(deleted)
    }

    @Test
    fun `loadMemories returns all memories`() {
        store.saveMemory("a", "first")
        store.saveMemory("b", "second")

        val memories = store.loadMemories()
        assertEquals(2, memories.size)
        assertEquals(setOf("a", "b"), memories.map { it.key }.toSet())
    }

    @Test
    fun `empty store returns empty results`() {
        assertTrue(store.loadRecentMessages(10).isEmpty())
        assertTrue(store.loadMemories().isEmpty())
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

        store.saveMemory("only_in_first", "value")
        assertTrue(store2.loadMemories().isEmpty())

        store2.close()
    }
}
