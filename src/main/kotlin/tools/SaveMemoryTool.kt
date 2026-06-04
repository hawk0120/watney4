package tools

import utils.MemoryStore

class SaveMemoryTool(private val memory: MemoryStore) : Tool {
    override val name = "save_memory"
    override val description = "Save an important fact about the user, a preference, project detail, or anything worth remembering across conversations. Use this when the user tells you something they'd want you to recall later."
    override val parameters = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "key" to mapOf(
                "type" to "string",
                "description" to "A short label for this memory, like 'user_name', 'current_project', 'favorite_tool'"
            ),
            "content" to mapOf(
                "type" to "string",
                "description" to "The detail to remember"
            )
        ),
        "required" to listOf("key", "content")
    )

    override suspend fun execute(arguments: Map<String, Any?>, progress: ((String) -> Unit)?): String {
        val key = arguments["key"] as? String ?: return "Error: 'key' is required"
        val content = arguments["content"] as? String ?: return "Error: 'content' is required"
        memory.saveMemory(key, content)
        return "Saved memory: $key = $content"
    }
}
