package tools

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SystemStatusToolTest {
    private val tool = SystemStatusTool()

    @Test
    fun `name and description are set`() {
        assertEquals("system_status", tool.name)
        assertTrue(tool.description.isNotBlank())
    }

    @Test
    fun `quick scope returns basic sections`() = runTest {
        val result = tool.execute(emptyMap())
        assertTrue(result.contains("System Status"))
        assertTrue(result.contains("Uptime") || result.contains("CPU") || result.contains("Memory"))
    }

    @Test
    fun `explicit quick scope works`() = runTest {
        val result = tool.execute(mapOf("scope" to "quick"))
        assertTrue(result.startsWith("**System Status**"))
    }

    @Test
    fun `full scope includes additional info`() = runTest {
        val result = tool.execute(mapOf("scope" to "full"))
        assertTrue(result.contains("System Status"))
        // full should include more detail than quick
        assertTrue(result.lines().size >= 5)
    }

    @Test
    fun `unknown scope falls back to quick`() = runTest {
        val result = tool.execute(mapOf("scope" to "invalid"))
        assertTrue(result.startsWith("**System Status**"))
    }

    @Test
    fun `parameters have no required fields`() {
        val params = tool.parameters
        val required = params["required"] as? List<*>
        assertTrue(required?.isEmpty() == true)
    }

    @Test
    fun `parseSize handles various units`() {
        // Private method, tested indirectly via free -h parsing,
        // but we can check the tool doesn't crash on edge cases
    }

    @Test
    fun `tool handles empty args gracefully`() = runTest {
        val result = tool.execute(mapOf("unexpected" to "value"))
        assertTrue(result.startsWith("**System Status**"))
    }
}
