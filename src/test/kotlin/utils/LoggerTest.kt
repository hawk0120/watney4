package utils

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class LoggerTest {
    private val outContent = ByteArrayOutputStream()
    private val originalOut = System.out

    @BeforeEach
    fun setUp() {
        System.setOut(PrintStream(outContent))
    }

    @AfterEach
    fun tearDown() {
        System.setOut(originalOut)
    }

    @Test
    fun `info log is printed at default INFO level`() {
        val log = Logger("TestLogger")
        log.info("hello world")
        val output = outContent.toString()
        assertTrue(output.contains("INFO"))
        assertTrue(output.contains("TestLogger"))
        assertTrue(output.contains("hello world"))
    }

    @Test
    fun `debug log is suppressed at default INFO level`() {
        val log = Logger("TestLogger")
        log.debug("should not appear")
        assertTrue(outContent.toString().isEmpty())
    }

    @Test
    fun `trace log is suppressed at default INFO level`() {
        val log = Logger("TestLogger")
        log.trace("invisible")
        assertTrue(outContent.toString().isEmpty())
    }

    @Test
    fun `warn log is printed at default INFO level`() {
        val log = Logger("TestLogger")
        log.warn("caution")
        val output = outContent.toString()
        assertTrue(output.contains("WARN"))
        assertTrue(output.contains("caution"))
    }

    @Test
    fun `error log is printed at default INFO level`() {
        val log = Logger("TestLogger")
        log.error("fail")
        val output = outContent.toString()
        assertTrue(output.contains("ERROR"))
        assertTrue(output.contains("fail"))
    }

    @Test
    fun `debug log is printed at DEBUG level`() {
        val log = Logger("TestLogger", LogLevel.DEBUG)
        log.debug("visible now")
        val output = outContent.toString()
        assertTrue(output.contains("DEBUG"))
        assertTrue(output.contains("visible now"))
    }

    @Test
    fun `trace log is suppressed at DEBUG level`() {
        val log = Logger("TestLogger", LogLevel.DEBUG)
        log.trace("still invisible")
        assertTrue(outContent.toString().isEmpty())
    }

    @Test
    fun `nothing is printed at ERROR level for info`() {
        val log = Logger("TestLogger", LogLevel.ERROR)
        log.info("should not appear")
        log.warn("should not appear either")
        assertTrue(outContent.toString().isEmpty())
    }

    @Test
    fun `error log is printed at ERROR level`() {
        val log = Logger("TestLogger", LogLevel.ERROR)
        log.error("critical")
        assertTrue(outContent.toString().contains("critical"))
    }

    @Test
    fun `companion getLogger creates logger`() {
        val log = Logger.getLogger("CompanionTest")
        log.info("from companion")
        assertTrue(outContent.toString().contains("CompanionTest"))
    }

    @Test
    fun `LogLevel priority values are correct`() {
        assertTrue(LogLevel.TRACE.priority < LogLevel.DEBUG.priority)
        assertTrue(LogLevel.DEBUG.priority < LogLevel.INFO.priority)
        assertTrue(LogLevel.INFO.priority < LogLevel.WARN.priority)
        assertTrue(LogLevel.WARN.priority < LogLevel.ERROR.priority)
    }
}
