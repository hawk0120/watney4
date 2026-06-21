package utils

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

class LTMemoryManagerTest {
    private lateinit var memory: MemoryStore
    private lateinit var llm: LLMProvider
    private lateinit var ltm: LTMemoryManager

    @BeforeEach
    fun setUp() {
        memory = MemoryStore(":memory:")
        memory.init()
        llm = mockk()
        ltm = LTMemoryManager(memory, llm)
    }

    @AfterEach
    fun tearDown() {
        memory.close()
    }

    @Test
    fun `getCurrentWeekStart returns Monday`() {
        val weekStart = ltm.getCurrentWeekStart()
        assertTrue(weekStart.matches(Regex("""\d{4}-\d{2}-\d{2}""")))
    }

    @Test
    fun `addSessionSummary saves to current week`() {
        val id = ltm.addSessionSummary("Test session content")
        assertTrue(id > 0)

        val weekStart = ltm.getCurrentWeekStart()
        val entries = memory.getLTMEntries(weekStart)
        assertEquals(1, entries.size)
        assertEquals("Test session content", entries[0].content)
    }

    @Test
    fun `loadCurrentWeekMemories returns formatted string`() = runBlocking {
        ltm.addSessionSummary("First session")
        ltm.addSessionSummary("Second session")

        val result = ltm.loadCurrentWeekMemories()
        assertTrue(result.contains("First session"))
        assertTrue(result.contains("Second session"))
        assertTrue(result.contains("Current Week LTM"))
    }

    @Test
    fun `loadCurrentWeekMemories returns empty for no entries`() = runBlocking {
        val result = ltm.loadCurrentWeekMemories()
        assertEquals("", result)
    }

    @Test
    fun `semanticSearch returns results`() = runBlocking {
        val embedding1 = floatArrayOf(1f, 0f, 0f)
        val embedding2 = floatArrayOf(0f, 1f, 0f)

        coEvery { llm.embed(any()) } returns embedding1

        memory.saveToArchive(
            originalEntryId = null, weekStart = "2026-06-01",
            content = "Found memory about cats", perspective = "factual",
            semanticTags = "cats, animals", embedding = MemoryStore.embeddingToBytes(floatArrayOf(0.9f, 0.1f, 0f))
        )
        memory.saveToArchive(
            originalEntryId = null, weekStart = "2026-06-01",
            content = "Unrelated about math", perspective = "factual",
            semanticTags = "math, numbers", embedding = MemoryStore.embeddingToBytes(floatArrayOf(0f, 0f, 0.9f))
        )

        val result = ltm.semanticSearch("find cats")
        assertTrue(result.contains("cats"))
        assertTrue(result.contains("similarity"))
    }

    @Test
    fun `semanticSearch handles empty archive`() = runBlocking {
        coEvery { llm.embed(any()) } returns floatArrayOf(0.1f, 0.2f)

        val result = ltm.semanticSearch("anything")
        assertTrue(result.contains("No archived memories"))
    }

    @Test
    fun `cosine similarity identical vectors`() {
        val a = floatArrayOf(1f, 2f, 3f)
        val b = floatArrayOf(1f, 2f, 3f)
        assertEquals(1f, LTMemoryManager.cosineSimilarity(a, b), 1e-6f)
    }

    @Test
    fun `cosine similarity orthogonal vectors`() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(0f, 1f)
        assertEquals(0f, LTMemoryManager.cosineSimilarity(a, b), 1e-6f)
    }

    @Test
    fun `cosine similarity opposite vectors`() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(-1f, 0f)
        assertEquals(-1f, LTMemoryManager.cosineSimilarity(a, b), 1e-6f)
    }

    @Test
    fun `cosine similarity zero vector`() {
        val a = floatArrayOf(0f, 0f)
        val b = floatArrayOf(1f, 0f)
        assertEquals(0f, LTMemoryManager.cosineSimilarity(a, b), 1e-6f)
    }

    @Test
    fun `cosine similarity scaled vectors`() {
        val a = floatArrayOf(1f, 2f, 3f)
        val b = floatArrayOf(2f, 4f, 6f)
        val dot = 1f * 2f + 2f * 4f + 3f * 6f
        val normA = sqrt(1f + 4f + 9f)
        val normB = sqrt(4f + 16f + 36f)
        val expected = dot / (normA * normB)
        assertEquals(expected, LTMemoryManager.cosineSimilarity(a, b), 1e-6f)
    }
}
