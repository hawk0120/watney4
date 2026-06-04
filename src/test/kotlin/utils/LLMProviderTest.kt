package utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.ToolCall

class LLMProviderTest {

    @Test
    fun `ChatMessage with all fields`() {
        val calls = listOf(ToolCall("1", "read", mapOf("path" to "/tmp/test")))
        val msg = ChatMessage(
            role = "assistant",
            content = "Let me check",
            toolCalls = calls,
            toolCallId = "call_1",
            name = "read_file"
        )
        assertEquals("assistant", msg.role)
        assertEquals("Let me check", msg.content)
        assertEquals(calls, msg.toolCalls)
        assertEquals("call_1", msg.toolCallId)
        assertEquals("read_file", msg.name)
    }

    @Test
    fun `ChatMessage with null content`() {
        val msg = ChatMessage(role = "user", content = null)
        assertNull(msg.content)
    }

    @Test
    fun `ChatMessage default values`() {
        val msg = ChatMessage(role = "user", content = "hi")
        assertNull(msg.toolCalls)
        assertNull(msg.toolCallId)
        assertNull(msg.name)
    }

    @Test
    fun `LLMResult Success`() {
        val result = LLMResult.Success("response text")
        assertInstanceOf(LLMResult.Success::class.java, result)
        assertEquals("response text", (result as LLMResult.Success).response)
        assertNull(result.calls)
    }

    @Test
    fun `LLMResult Success with tool calls`() {
        val calls = listOf(ToolCall("1", "read", mapOf("path" to "x")))
        val result = LLMResult.Success("using tools", calls)
        assertEquals(calls, (result as LLMResult.Success).calls)
    }

    @Test
    fun `LLMResult Error`() {
        val result = LLMResult.Error("something broke")
        assertInstanceOf(LLMResult.Error::class.java, result)
        assertEquals("something broke", (result as LLMResult.Error).message)
    }

    @Test
    fun `LLMResult is sealed`() {
        val success: LLMResult = LLMResult.Success("ok")
        val error: LLMResult = LLMResult.Error("fail")
        assertInstanceOf(LLMResult.Success::class.java, success)
        assertInstanceOf(LLMResult.Error::class.java, error)
    }
}
