package tools

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ToolRegistryTest {

    private val toolA = object : Tool {
        override val name = "alpha"
        override val description = "Tool A"
        override val parameters = mapOf("type" to "object", "properties" to emptyMap<String, Any>())
        override suspend fun execute(args: Map<String, Any?>, progress: ((String) -> Unit)?) = "result_a"
    }

    private val toolB = object : Tool {
        override val name = "beta"
        override val description = "Tool B"
        override val parameters = mapOf("type" to "object", "properties" to emptyMap<String, Any>())
        override suspend fun execute(args: Map<String, Any?>, progress: ((String) -> Unit)?) = "result_b"
    }

    private val registry = ToolRegistry(listOf(toolA, toolB))

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `definitions returns all tools in correct format`() {
        val defs = registry.definitions()
        assertEquals(2, defs.size)

        val alphaDef = defs.find { (it["function"] as Map<String, Any>)["name"] == "alpha" }
        assertNotNull(alphaDef)
        val ad = alphaDef!!
        assertEquals("function", ad["type"])
        assertEquals("Tool A", (ad["function"] as Map<String, Any>?)?.get("description"))
    }

    @Test
    fun `execute dispatches to correct tool`() = runTest {
        val result = registry.execute("alpha", emptyMap())
        assertEquals("result_a", result)
    }

    @Test
    fun `execute returns error for unknown tool`() = runTest {
        val result = registry.execute("unknown", emptyMap())
        assertEquals("Error: unknown tool 'unknown'", result)
    }

    @Test
    fun `execute catches exceptions from tools`() = runTest {
        val broken = object : Tool {
            override val name = "broken"
            override val description = "Broken tool"
            override val parameters = emptyMap<String, Any>()
            override suspend fun execute(args: Map<String, Any?>, progress: ((String) -> Unit)?): String {
                throw RuntimeException("kaboom")
            }
        }
        val reg = ToolRegistry(listOf(broken))
        val result = reg.execute("broken", emptyMap())
        assertEquals("Error executing broken: kaboom", result)
    }

    @Test
    fun `execute passes args and progress to tool`() = runTest {
        var capturedArgs: Map<String, Any?>? = null
        var capturedProgress: ((String) -> Unit)? = null
        val spy = object : Tool {
            override val name = "spy"
            override val description = "Spy tool"
            override val parameters = emptyMap<String, Any>()
            override suspend fun execute(args: Map<String, Any?>, progress: ((String) -> Unit)?): String {
                capturedArgs = args
                capturedProgress = progress
                return "spied"
            }
        }
        val reg = ToolRegistry(listOf(spy))
        val progress: (String) -> Unit = {}
        reg.execute("spy", mapOf("key" to "val"), progress)
        assertEquals(mapOf("key" to "val"), capturedArgs)
        assertSame(progress, capturedProgress)
    }

    @Test
    fun `ToolCall data class`() {
        val call = ToolCall(id = "call_abc", name = "read", arguments = mapOf("path" to "/x"))
        assertEquals("call_abc", call.id)
        assertEquals("read", call.name)
        assertEquals(mapOf("path" to "/x"), call.arguments)
    }

    @Test
    fun `empty registry`() {
        val empty = ToolRegistry(emptyList())
        assertTrue(empty.definitions().isEmpty())
    }

    @Test
    fun `execute on empty registry returns error`() = runTest {
        val empty = ToolRegistry(emptyList())
        val result = empty.execute("anything", emptyMap())
        assertEquals("Error: unknown tool 'anything'", result)
    }
}
