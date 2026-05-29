package utils

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class WriteTool : Tool {
    override val name = "write"
    override val description = "Write content to a file. Creates parent directories if needed. Overwrites by default."
    override val parameters = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "path" to mapOf(
                "type" to "string",
                "description" to "Absolute path where to write the file"
            ),
            "content" to mapOf(
                "type" to "string",
                "description" to "Content to write to the file"
            )
        ),
        "required" to listOf("path", "content")
    )

    override suspend fun execute(args: Map<String, Any?>, progress: ((String) -> Unit)?): String {
        val path = args["path"] as? String ?: return "Error: missing 'path' argument"
        val content = args["content"] as? String ?: return "Error: missing 'content' argument"
        val file = Path.of(path)
        Files.createDirectories(file.parent)
        Files.writeString(file, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        return "Wrote ${content.length} chars to $path"
    }
}
