package utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ContextTest {
    private lateinit var ctx: Context
    private lateinit var messages: MutableList<ChatMessage>

    @BeforeEach
    fun setUp() {
        ctx = Context()
        messages = mutableListOf(
            ChatMessage("system", "You are a helpful assistant"),
            ChatMessage("user", "hello"),
            ChatMessage("assistant", "hi there"),
            ChatMessage("user", "how are you?"),
            ChatMessage("assistant", "I'm doing great!"),
            ChatMessage("user", "tell me a joke"),
            ChatMessage("assistant", "Why did the chicken cross the road?")
        )
        ctx.bind(messages)
    }

    @Test
    fun `bind sets up context`() {
        assertTrue(ctx.isBound())
        assertEquals(7, ctx.size)
    }

    @Test
    fun `isBound returns false before bind`() {
        val c = Context()
        assertFalse(c.isBound())
    }

    @Test
    fun `toList returns copy`() {
        val list = ctx.toList()
        assertEquals(7, list.size)
        assertEquals("system", list[0].role)
        assertEquals("user", list[1].role)
    }

    @Test
    fun `add appends message`() {
        ctx.add(ChatMessage("user", "new message"))
        assertEquals(8, ctx.size)
        assertEquals("new message", messages.last().content)
    }

    @Test
    fun `truncate removes oldest non-system messages`() {
        val result = ctx.truncate(keepLast = 2)
        assertTrue(result.contains("Truncated"))
        assertTrue(result.contains("2"))
        // Should keep: system + last 2 non-system
        assertEquals(3, ctx.size)
        assertEquals("system", messages[0].role)
        assertEquals("tell me a joke", messages[1].content)
        assertEquals("Why did the chicken cross the road?", messages[2].content)
    }

    @Test
    fun `truncate with keep larger than current size does nothing`() {
        val result = ctx.truncate(keepLast = 100)
        assertTrue(result.contains("Nothing to truncate"))
        assertEquals(7, ctx.size)
    }

    @Test
    fun `truncate with keep=1 keeps only system prompt and one message`() {
        ctx.truncate(keepLast = 1)
        assertEquals(2, ctx.size)
        assertEquals("system", messages[0].role)
    }

    @Test
    fun `truncate with no non-system messages`() {
        val c = Context()
        val msgs = mutableListOf(ChatMessage("system", "sys"))
        c.bind(msgs)
        val result = c.truncate(keepLast = 5)
        assertEquals("Nothing to truncate — 0 non-system messages (≤ 5)", result)
    }

    @Test
    fun `inject appends at end by default`() {
        ctx.inject("system", "remember: be concise")
        assertEquals(8, ctx.size)
        assertEquals("remember: be concise", messages.last().content)
        assertEquals("system", messages.last().role)
    }

    @Test
    fun `inject at specific position`() {
        ctx.inject("system", "mid-message", index = 3)
        assertEquals(8, ctx.size)
        assertEquals("mid-message", messages[3].content)
        assertEquals("how are you?", messages[4].content) // shifted
    }

    @Test
    fun `inject at position 0`() {
        ctx.inject("system", "at start", index = 0)
        assertEquals(8, ctx.size)
        assertEquals("at start", messages[0].content)
        assertEquals("You are a helpful assistant", messages[1].content)
    }

    @Test
    fun `inject at position beyond end appends`() {
        ctx.inject("user", "end", index = 100)
        assertEquals(8, ctx.size)
        assertEquals("end", messages.last().content)
    }

    @Test
    fun `clearNonSystem removes all non-system messages`() {
        val count = ctx.clearNonSystem()
        assertEquals(6, count)
        assertEquals(1, ctx.size)
        assertEquals("system", messages[0].role)
    }

    @Test
    fun `clearNonSystem with only system messages`() {
        val c = Context()
        val msgs = mutableListOf(ChatMessage("system", "sys"))
        c.bind(msgs)
        val count = c.clearNonSystem()
        assertEquals(0, count)
        assertEquals(1, c.size)
    }

    @Test
    fun `methods throw when not bound`() {
        val c = Context()
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            c.truncate(5)
        }
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            c.inject("user", "test")
        }
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            c.clearNonSystem()
        }
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            c.toList()
        }
    }

    @Test
    fun `truncate returns non-system count message`() {
        val result = ctx.truncate(keepLast = 2)
        assertTrue(result.contains("6 non-system messages"))
    }
}
