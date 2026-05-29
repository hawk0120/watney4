package utils

import java.nio.file.Files
import java.nio.file.Path

class ReadTool : Tool {
    override val name = "read"
    override val description = "Read a file from disk. Returns the full contents."
    override val parameters = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "path" to mapOf(
                "type" to "string",
                "description" to "Absolute path to the file to read"
            )
        ),
        "required" to listOf("path")
    )

    override suspend fun execute(args: Map<String, Any?>, progress: ((String) -> Unit)?): String {
        val path = args["path"] as? String ?: return "Error: missing 'path' argument"
        val file = Path.of(path)
        if (!Files.exists(file)) return "Error: file not found: $path"
        if (!Files.isRegularFile(file)) return "Error: not a file: $path"
        return Files.readString(file)
    }
}
