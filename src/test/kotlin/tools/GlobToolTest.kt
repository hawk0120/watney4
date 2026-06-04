package tools

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class GlobToolTest {
    private val tool = GlobTool()

    @Test
    fun `glob finds matching files`() = runTest {
        val dir = Files.createTempDirectory("globtestdir")
        val file = Files.createFile(dir.resolve("test.md"))

        val result = tool.execute(mapOf("pattern" to "*.md", "root" to dir.toString()))
        assertTrue(result.contains(file.toString()))
    }

    @Test
    fun `glob with no matches returns appropriate message`() = runTest {
        val dir = Files.createTempDirectory("globemptydir")

        val result = tool.execute(mapOf("pattern" to "*.nonexistent_ext_xyz", "root" to dir.toString()))
        assertTrue(result.startsWith("No files matching"))
    }

    @Test
    fun `glob missing pattern returns error`() = runTest {
        val result = tool.execute(emptyMap())
        assertEquals("Error: missing 'pattern' argument", result)
    }

    @Test
    fun `glob with invalid root returns error`() = runTest {
        val result = tool.execute(mapOf("pattern" to "*.kt", "root" to "/nonexistent_path_xyz"))
        assertEquals("Error: root directory not found: /nonexistent_path_xyz", result)
    }

    @Test
    fun `glob with recursive pattern`() = runTest {
        val root = Files.createTempDirectory("globroot")
        val subdir = root.resolve("sub")
        Files.createDirectories(subdir)
        val nested = Files.createFile(subdir.resolve("nested.txt"))

        val result = tool.execute(mapOf("pattern" to "**/*.txt", "root" to root.toString()))
        assertTrue(result.contains(nested.toString()))
    }

    @Test
    fun `glob respects maxResults`() = runTest {
        val root = Files.createTempDirectory("globmax")
        repeat(5) { Files.createFile(root.resolve("file$it.txt")) }

        val result = tool.execute(mapOf(
            "pattern" to "*.txt",
            "root" to root.toString(),
            "maxResults" to 2.0
        ))

        val lines = result.lines()
        assertTrue(lines.size <= 2)
    }

    @Test
    fun `glob maxResults is clamped to 200`() = runTest {
        val root = Files.createTempDirectory("globclamp")
        repeat(3) { Files.createFile(root.resolve("f$it.txt")) }

        val result = tool.execute(mapOf(
            "pattern" to "*.txt",
            "root" to root.toString(),
            "maxResults" to 500.0
        ))

        assertFalse(result.startsWith("Error"))
    }
}
