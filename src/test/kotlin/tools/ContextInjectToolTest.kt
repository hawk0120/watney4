package tools

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.ChatMessage
import utils.Context

class ContextInjectToolTest {
    private lateinit var ctx: Context
    private lateinit var tool: ContextInjectTool

    @BeforeEach
    fun setUp() {
        ctx = Context()
        val messages = mutableListOf(
            ChatMessage("system", "sys"),
            ChatMessage("user", "hello")
        )
        ctx.bind(messages)
        tool = ContextInjectTool(ctx)
    }

    @Test
    fun `name and description are set`() {
        assertEquals("context_inject", tool.name)
        assertTrue(tool.description.isNotBlank())
    }

    @Test
    fun `inject appends message`() = runTest {
        val result = tool.execute(mapOf("role" to "system", "content" to "remember: be concise"))
        assertTrue(result.contains("Injected"))
        assertTrue(result.contains("system"))
        assertEquals(3, ctx.size)
    }

    @Test
    fun `inject at specific position`() = runTest {
        tool.execute(mapOf("role" to "user", "content" to "mid", "index" to 1.0))
        assertEquals(3, ctx.size)
        assertEquals("mid", ctx.toList()[1].content)
    }

    @Test
    fun `inject missing role returns error`() = runTest {
        val result = tool.execute(mapOf("content" to "test"))
        assertEquals("Error: missing 'role' argument", result)
    }

    @Test
    fun `inject missing content returns error`() = runTest {
        val result = tool.execute(mapOf("role" to "user"))
        assertEquals("Error: missing 'content' argument", result)
    }

    @Test
    fun `inject with both missing returns role error`() = runTest {
        val result = tool.execute(emptyMap())
        assertEquals("Error: missing 'role' argument", result)
    }

    @Test
    fun `inject at position 0 is clamped after system message`() = runTest {
        tool.execute(mapOf("role" to "system", "content" to "clamped", "index" to 0.0))
        assertEquals(3, ctx.size)
        // Clamped to position 1, after system message at 0
        assertEquals("sys", ctx.toList()[0].content)
        assertEquals("clamped", ctx.toList()[1].content)
    }

    @Test
    fun `parameters include required fields`() {
        val params = tool.parameters
        val required = params["required"] as? List<*>
        assertTrue(required?.contains("role") == true)
        assertTrue(required?.contains("content") == true)
    }
}
