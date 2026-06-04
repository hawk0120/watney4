package tools

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class GrepToolTest {
    private val tool = GrepTool()

    @Test
    fun `grep finds matching pattern in file`() = runTest {
        val root = Files.createTempDirectory("greproot")
        Files.writeString(root.resolve("greptest.txt"), "hello world\nfoo bar")

        val result = tool.execute(mapOf("pattern" to "hello", "root" to root.toString()))
        assertTrue(result.contains("greptest.txt"))
        assertTrue(result.contains("hello world"))
    }

    @Test
    fun `grep with no matches returns appropriate message`() = runTest {
        val root = Files.createTempDirectory("grepempty")
        Files.writeString(root.resolve("empty.txt"), "nothing here")

        val result = tool.execute(mapOf("pattern" to "xyzzy", "root" to root.toString()))
        assertTrue(result.startsWith("No matches for"))
    }

    @Test
    fun `grep missing pattern returns error`() = runTest {
        val result = tool.execute(emptyMap())
        assertEquals("Error: missing 'pattern' argument", result)
    }

    @Test
    fun `grep with invalid root returns error`() = runTest {
        val result = tool.execute(mapOf("pattern" to "test", "root" to "/nonexistent_xyz"))
        assertEquals("Error: root directory not found: /nonexistent_xyz", result)
    }

    @Test
    fun `grep with invalid regex returns error`() = runTest {
        val result = tool.execute(mapOf("pattern" to "[invalid", "root" to "/tmp"))
        assertTrue(result.startsWith("Error: invalid regex"))
    }

    @Test
    fun `grep respects filePattern filter`() = runTest {
        val root = Files.createTempDirectory("grepfilter")
        Files.writeString(root.resolve("match.kt"), "fun main() = println(\"hi\")")
        Files.writeString(root.resolve("skip.py"), "fun main() = print(\"hi\")")

        val result = tool.execute(mapOf(
            "pattern" to "fun",
            "root" to root.toString(),
            "filePattern" to "*.kt"
        ))
        assertTrue(result.contains("match.kt"))
        assertFalse(result.contains("skip.py"))
    }

    @Test
    fun `grep respects maxResults`() = runTest {
        val root = Files.createTempDirectory("grepmax")
        Files.writeString(root.resolve("many.txt"), (1..100).joinToString("\n") { "match line $it" })

        val result = tool.execute(mapOf(
            "pattern" to "match",
            "root" to root.toString(),
            "maxResults" to 5.0
        ))
        val lines = result.lines()
        assertTrue(lines.size <= 5)
    }

    @Test
    fun `grep returns line numbers`() = runTest {
        val root = Files.createTempDirectory("greplineno")
        Files.writeString(root.resolve("lines.txt"), "first\nsecond\nthird")

        val result = tool.execute(mapOf("pattern" to "second", "root" to root.toString()))
        assertTrue(result.contains("lines.txt:2"))
    }
}
