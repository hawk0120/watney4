package tools

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class BashTool(
    private val timeoutSeconds: Long = 30
) : Tool {
    override val name = "bash"
    override val description = "Execute a shell command. Returns stdout + stderr combined."
    override val parameters = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "command" to mapOf(
                "type" to "string",
                "description" to "Shell command to execute"
            ),
            "timeout" to mapOf(
                "type" to "number",
                "description" to "Timeout in seconds (default 30, max 120)"
            )
        ),
        "required" to listOf("command")
    )

    private val dangerousCommands = listOf(
        "rm -rf /", "rm -rf /*", "sudo ", "shutdown", "reboot", "halt",
        "mkfs", "dd ", ":(){ :|:& };:", "chmod 000", "> /dev/sda",
        "format", "fdisk", "mkswap"
    )

    override suspend fun execute(args: Map<String, Any?>, progress: ((String) -> Unit)?): String {
        val command = args["command"] as? String ?: return "Error: missing 'command' argument"

        for (dangerous in dangerousCommands) {
            if (command.contains(dangerous, ignoreCase = true)) {
                return "Error: command blocked for safety: contains '$dangerous'"
            }
        }

        val timeout = ((args["timeout"] as? Double)?.toLong() ?: timeoutSeconds)
            .coerceIn(1, 120)

        return try {
            val process = ProcessBuilder("bash", "-c", command)
                .redirectErrorStream(true)
                .start()

            val finished = process.waitFor(timeout, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return "Error: command timed out after ${timeout}s"
            }

            val output = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.exitValue()
            if (output.isEmpty()) "Exit code: $exitCode (no output)"
            else "Exit code: $exitCode\n$output"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}
