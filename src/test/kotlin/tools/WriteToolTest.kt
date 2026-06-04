package tools

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class WriteToolTest {
    private val tool = WriteTool()
    private val tmpDir = Files.createTempDirectory("writetool")

    @Test
    fun `write creates file with content`() = runTest {
        val path = tmpDir.resolve("test.txt").toString()
        val result = tool.execute(mapOf("path" to path, "content" to "hello"))
        assertTrue(result.startsWith("Wrote"))
        assertEquals("hello", Files.readString(Path.of(path)))
    }

    @Test
    fun `write returns char count`() = runTest {
        val path = tmpDir.resolve("count.txt").toString()
        val result = tool.execute(mapOf("path" to path, "content" to "12345"))
        assertEquals("Wrote 5 chars to $path", result)
    }

    @Test
    fun `write overwrites existing file`() = runTest {
        val path = tmpDir.resolve("overwrite.txt").toString()
        tool.execute(mapOf("path" to path, "content" to "first"))
        tool.execute(mapOf("path" to path, "content" to "second"))
        assertEquals("second", Files.readString(Path.of(path)))
    }

    @Test
    fun `write creates parent directories`() = runTest {
        val path = tmpDir.resolve("a/b/c/deep.txt").toString()
        tool.execute(mapOf("path" to path, "content" to "nested"))
        assertEquals("nested", Files.readString(Path.of(path)))
    }

    @Test
    fun `write missing path returns error`() = runTest {
        val result = tool.execute(mapOf("content" to "data"))
        assertEquals("Error: missing 'path' argument", result)
    }

    @Test
    fun `write missing content returns error`() = runTest {
        val result = tool.execute(mapOf("path" to "/tmp/x"))
        assertEquals("Error: missing 'content' argument", result)
    }

    @Test
    fun `write empty content`() = runTest {
        val path = tmpDir.resolve("empty.txt").toString()
        val result = tool.execute(mapOf("path" to path, "content" to ""))
        assertEquals("Wrote 0 chars to $path", result)
        assertEquals("", Files.readString(Path.of(path)))
    }
}
