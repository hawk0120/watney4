package tools

import utils.MemoryStore

class ForgetMemoryTool(private val memory: MemoryStore) : Tool {
    override val name = "forget_memory"
    override val description = "Delete a saved memory by its key name. Use this when the user tells you a memory is wrong or no longer relevant."
    override val parameters = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "key" to mapOf(
                "type" to "string",
                "description" to "The key of the memory to delete"
            )
        ),
        "required" to listOf("key")
    )

    override suspend fun execute(arguments: Map<String, Any?>, progress: ((String) -> Unit)?): String {
        val key = arguments["key"] as? String ?: return "Error: 'key' is required"
        return if (memory.deleteMemory(key)) "Forgot memory: $key"
        else "No memory found with key: $key"
    }
}
