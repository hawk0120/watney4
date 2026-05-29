package utils

import java.nio.file.Files
import java.nio.file.Path

class GrepTool : Tool {
    override val name = "grep"
    override val description = "Search file contents using a regular expression. Returns matching file paths and line numbers."
    override val parameters = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "pattern" to mapOf(
                "type" to "string",
                "description" to "Regular expression to search for"
            ),
            "root" to mapOf(
                "type" to "string",
                "description" to "Root directory to search (default: /home/hawk0120)"
            ),
            "filePattern" to mapOf(
                "type" to "string",
                "description" to "Glob pattern to filter files (e.g. *.kt, *.{kt,java})"
            ),
            "maxResults" to mapOf(
                "type" to "number",
                "description" to "Maximum matches to return (default 30, max 100)"
            )
        ),
        "required" to listOf("pattern")
    )

    override suspend fun execute(args: Map<String, Any?>, progress: ((String) -> Unit)?): String {
        val pattern = args["pattern"] as? String ?: return "Error: missing 'pattern' argument"
        val root = args["root"] as? String ?: "/home/hawk0120"
        val filePattern = args["filePattern"] as? String
        val maxResults = ((args["maxResults"] as? Double)?.toInt() ?: 30).coerceIn(1, 100)

        val rootPath = Path.of(root)
        if (!Files.isDirectory(rootPath)) return "Error: root directory not found: $root"

        val regex = try {
            Regex(pattern)
        } catch (e: Exception) {
            return "Error: invalid regex '$pattern': ${e.message}"
        }

        val fileMatcher = filePattern?.let { fp ->
            java.nio.file.FileSystems.getDefault().getPathMatcher("glob:$fp")
        }

        val results = mutableListOf<String>()
        var matchCount = 0

        Files.walk(rootPath).use { stream ->
            stream.filter { Files.isRegularFile(it) }
                .filter { path ->
                    fileMatcher == null || fileMatcher.matches(rootPath.relativize(path))
                }
                .forEach { path ->
                    if (matchCount >= maxResults) return@forEach
                    try {
                        val lines = Files.readAllLines(path)
                        for ((i, line) in lines.withIndex()) {
                            if (matchCount >= maxResults) break
                            if (regex.containsMatchIn(line)) {
                                val relative = rootPath.relativize(path)
                                results.add("${relative}:${i + 1}: ${line.trim().take(120)}")
                                matchCount++
                            }
                        }
                    } catch (_: Exception) {
                        // skip unreadable files
                    }
                }
        }

        if (results.isEmpty()) return "No matches for '$pattern' under $root"
        return results.joinToString("\n")
    }
}
