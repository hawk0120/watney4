package tools

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.ChatMessage
import utils.Context

class ContextTruncateToolTest {
    private lateinit var ctx: Context
    private lateinit var tool: ContextTruncateTool

    @BeforeEach
    fun setUp() {
        ctx = Context()
        val messages = mutableListOf(
            ChatMessage("system", "sys"),
            ChatMessage("user", "msg1"),
            ChatMessage("assistant", "resp1"),
            ChatMessage("user", "msg2"),
            ChatMessage("assistant", "resp2")
        )
        ctx.bind(messages)
        tool = ContextTruncateTool(ctx)
    }

    @Test
    fun `name and description are set`() {
        assertEquals("context_truncate", tool.name)
        assertTrue(tool.description.isNotBlank())
    }

    @Test
    fun `truncate to 2 keeps system + 2`() = runTest {
        val result = tool.execute(mapOf("keepLast" to 2.0))
        assertTrue(result.startsWith("Truncated"))
        assertEquals(3, ctx.size)
    }

    @Test
    fun `truncate with missing argument returns error`() = runTest {
        val result = tool.execute(emptyMap())
        assertEquals("Error: missing or invalid 'keepLast' argument", result)
    }

    @Test
    fun `truncate with invalid argument returns error`() = runTest {
        val result = tool.execute(mapOf("keepLast" to "not-a-number"))
        assertEquals("Error: missing or invalid 'keepLast' argument", result)
    }

    @Test
    fun `truncate with keepLast 1`() = runTest {
        val result = tool.execute(mapOf("keepLast" to 1.0))
        assertTrue(result.startsWith("Truncated"))
        assertEquals(2, ctx.size) // system + 1
    }

    @Test
    fun `parameters include required`() {
        val params = tool.parameters
        assertEquals("object", params["type"])
        val required = params["required"] as? List<*>
        assertTrue(required?.contains("keepLast") == true)
    }
}
