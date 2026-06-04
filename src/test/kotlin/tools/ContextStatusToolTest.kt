package tools

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.ChatMessage
import utils.Context

class ContextStatusToolTest {
    private lateinit var ctx: Context
    private lateinit var tool: ContextStatusTool

    @BeforeEach
    fun setUp() {
        ctx = Context()
        val messages = mutableListOf(
            ChatMessage("system", "sys prompt"),
            ChatMessage("user", "hello"),
            ChatMessage("assistant", "hi"),
            ChatMessage("user", "how are you?")
        )
        ctx.bind(messages)
        tool = ContextStatusTool(ctx)
    }

    @Test
    fun `name and description are set`() {
        assertEquals("context_status", tool.name)
        assertTrue(tool.description.isNotBlank())
    }

    @Test
    fun `status returns context info`() = runTest {
        val result = tool.execute(emptyMap())
        assertTrue(result.contains("Total messages: 4"))
        assertTrue(result.contains("System: 1"))
        assertTrue(result.contains("User: 2"))
        assertTrue(result.contains("Assistant: 1"))
        assertTrue(result.contains("Total characters:"))
    }

    @Test
    fun `status works with empty args`() = runTest {
        val result = tool.execute(mapOf("unexpected" to "arg"))
        assertTrue(result.contains("Total messages:"))
    }

    @Test
    fun `parameters have no required fields`() {
        val params = tool.parameters
        val required = params["required"] as? List<*>
        assertTrue(required?.isEmpty() == true)
    }
}
