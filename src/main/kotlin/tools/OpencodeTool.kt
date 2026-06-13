package tools

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class OpencodeTool(
    private val defaultDir: String = "/home/hawk0120/dev/Watney4",
    private val defaultTimeout: Long = 300
) : Tool {
    override val name = "opencode"
    override val description = "Delegate a coding task to opencode — the full AI coding agent. Use this for complex multi-step work (debugging, refactoring, adding features, writing tests, etc.) that Watney4's simple tools can't handle alone. Returns the full conversation output."
    override val parameters = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "task" to mapOf(
                "type" to "string",
                "description" to "The coding task to give to opencode (e.g. 'fix the bug in src/main.rs', 'add a /status command to the Discord bot')"
            ),
            "dir" to mapOf(
                "type" to "string",
                "description" to "Project directory to run opencode in (default: /home/hawk0120/dev/Watney4)"
            ),
            "timeout" to mapOf(
                "type" to "number",
                "description" to "Timeout in seconds (default 300, max 600)"
            )
        ),
        "required" to listOf("task")
    )

    override suspend fun execute(args: Map<String, Any?>, progress: ((String) -> Unit)?): String {
        val task = args["task"] as? String ?: return "Error: missing 'task' argument"
        val dir = args["dir"] as? String ?: defaultDir
        val timeout = ((args["timeout"] as? Double)?.toLong() ?: defaultTimeout)
            .coerceIn(1, 600)

        progress?.invoke("`opencode run` started in **$dir**...")

        return try {
            val process = ProcessBuilder(
                "opencode", "run", task,
                "--dir", dir
            )
                .redirectErrorStream(true)
                .start()

            val output = StringBuilder()
            val reader = process.inputStream.bufferedReader()
            val done = AtomicBoolean(false)

            val readerThread = Thread {
                try {
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        synchronized(output) { output.appendLine(line) }
                        progress?.invoke(line!!)
                    }
                } catch (_: Exception) {
                } finally {
                    done.set(true)
                }
            }
            readerThread.isDaemon = true
            readerThread.start()

            val finished = process.waitFor(timeout, TimeUnit.SECONDS)
            done.set(true)
            readerThread.join(2000)

            if (!finished) {
                process.destroyForcibly()
                return "Error: opencode task timed out after ${timeout}s"
            }

            val result: String = synchronized(output) { output.toString().trim() }
            val exitCode = process.exitValue()
            if (result.isEmpty()) "Exit code: $exitCode (no output)"
            else result
        } catch (e: Exception) {
            "Error launching opencode: ${e.message}"
        }
    }
}
