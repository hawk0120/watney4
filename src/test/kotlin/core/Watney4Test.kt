package core

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Watney4Test {
    private val watney = Watney4()

    @Test
    fun `whoAmI returns non-empty string`() {
        val result = watney.whoAmI()
        assertNotNull(result)
        assertTrue(result.isNotBlank())
    }

    @Test
    fun `whoAmI contains key sections`() {
        val result = watney.whoAmI()
        assertTrue(result.contains("Watney4"))
        assertTrue(result.contains("Environment"))
        assertTrue(result.contains("Tools"))
        assertTrue(result.contains("Personality"))
        assertTrue(result.contains("Safety"))
    }

    @Test
    fun `whoAmI mentions all tools`() {
        val result = watney.whoAmI()
        val tools = listOf("read", "write", "bash", "glob", "grep", "web_search", "web_fetch",
            "cron", "memory_search", "semantic_search", "opencode",
            "context_truncate", "context_inject", "context_status", "system_status")
        for (tool in tools) {
            assertTrue(result.contains(tool), "whoAmI should mention tool: $tool")
        }
    }

    @Test
    fun `whoIsBrady returns non-empty string`() {
        val result = watney.whoIsBrady()
        assertNotNull(result)
        assertTrue(result.isNotBlank())
    }

    @Test
    fun `whoIsBrady mentions administrator role`() {
        val result = watney.whoIsBrady()
        assertTrue(result.contains("administrator"))
        assertTrue(result.contains("Brady"))
    }

    @Test
    fun `both methods complete without exceptions`() {
        assertDoesNotThrow { watney.whoAmI() }
        assertDoesNotThrow { watney.whoIsBrady() }
    }
}
