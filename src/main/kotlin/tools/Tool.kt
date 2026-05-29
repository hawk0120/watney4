package tools

interface Tool {
    val name: String
    val description: String
    val parameters: Map<String, Any>
    suspend fun execute(args: Map<String, Any?>, progress: ((String) -> Unit)? = null): String
}

data class ToolCall(
    val id: String,
    val name: String,
    val arguments: Map<String, Any?>
)

class ToolRegistry(private val tools: List<Tool>) {
    fun definitions(): List<Map<String, Any>> = tools.map { tool ->
        mapOf(
            "type" to "function",
            "function" to mapOf(
                "name" to tool.name,
                "description" to tool.description,
                "parameters" to tool.parameters
            )
        )
    }

    suspend fun execute(name: String, args: Map<String, Any?>, progress: ((String) -> Unit)? = null): String {
        val tool = tools.find { it.name == name }
            ?: return "Error: unknown tool '$name'"
        return try {
            tool.execute(args, progress)
        } catch (e: Exception) {
            "Error executing $name: ${e.message}"
        }
    }
}
