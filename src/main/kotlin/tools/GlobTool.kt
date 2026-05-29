package tools

import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

class GlobTool : Tool {
    override val name = "glob"
    override val description = "Find files matching a glob pattern. Supports ** for recursive matching."
    override val parameters = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "pattern" to mapOf(
                "type" to "string",
                "description" to "Glob pattern (e.g. **/*.kt, src/**/*.ts)"
            ),
            "root" to mapOf(
                "type" to "string",
                "description" to "Root directory to search from (default: /home/hawk0120)"
            ),
            "maxResults" to mapOf(
                "type" to "number",
                "description" to "Maximum number of results (default 50, max 200)"
            )
        ),
        "required" to listOf("pattern")
    )

    override suspend fun execute(args: Map<String, Any?>, progress: ((String) -> Unit)?): String {
        val pattern = args["pattern"] as? String ?: return "Error: missing 'pattern' argument"
        val root = args["root"] as? String ?: "/home/hawk0120"
        val maxResults = ((args["maxResults"] as? Double)?.toInt() ?: 50).coerceIn(1, 200)

        val rootPath = Path.of(root)
        if (!Files.isDirectory(rootPath)) return "Error: root directory not found: $root"

        val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
        val results = mutableListOf<String>()

        Files.walk(rootPath).use { stream ->
            stream.forEach { path ->
                if (results.size >= maxResults) return@forEach
                if (matcher.matches(rootPath.relativize(path))) {
                    results.add(path.toString())
                }
            }
        }

        if (results.isEmpty()) return "No files matching '$pattern' found under $root"
        val truncated = if (results.size > maxResults) {
            results.take(maxResults) + "... (${results.size - maxResults} more)"
        } else results

        return truncated.joinToString("\n")
    }
}
