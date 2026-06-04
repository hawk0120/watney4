package tools

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class ReadToolTest {
    private val tool = ReadTool()

    @Test
    fun `read existing file returns content`() = runTest {
        val file = Files.createTempFile("readtest", ".txt")
        file.toFile().deleteOnExit()
        Files.writeString(file, "hello world")

        val result = tool.execute(mapOf("path" to file.toString()))
        assertEquals("hello world", result)
    }

    @Test
    fun `read missing file returns error`() = runTest {
        val result = tool.execute(mapOf("path" to "/tmp/nonexistent_file_xyz"))
        assertTrue(result.startsWith("Error:"))
        assertTrue(result.contains("not found"))
    }

    @Test
    fun `read missing path argument returns error`() = runTest {
        val result = tool.execute(emptyMap())
        assertEquals("Error: missing 'path' argument", result)
    }

    @Test
    fun `read directory returns error`() = runTest {
        val result = tool.execute(mapOf("path" to "/tmp"))
        assertTrue(result.startsWith("Error:"))
    }

    @Test
    fun `read returns multiline content`() = runTest {
        val file = Files.createTempFile("readtest", ".txt")
        file.toFile().deleteOnExit()
        Files.writeString(file, "line1\nline2\nline3")

        val result = tool.execute(mapOf("path" to file.toString()))
        assertEquals("line1\nline2\nline3", result)
    }

    @Test
    fun `read with null path returns error`() = runTest {
        val result = tool.execute(mapOf("path" to null))
        assertEquals("Error: missing 'path' argument", result)
    }
}
