package tools

import utils.MemoryStore

class MemorySearchTool(private val memory: MemoryStore) : Tool {
    override val name = "memory_search"
    override val description = "Search past conversation messages by keyword. Use this to recall details from earlier in the chat history."
    override val parameters = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "query" to mapOf(
                "type" to "string",
                "description" to "Keyword or phrase to search for in past messages"
            ),
            "limit" to mapOf(
                "type" to "integer",
                "description" to "Maximum number of results to return (default 5, max 20)"
            )
        ),
        "required" to listOf("query")
    )

    override suspend fun execute(arguments: Map<String, Any?>, progress: ((String) -> Unit)?): String {
        val query = arguments["query"] as? String ?: return "Error: 'query' is required"
        val limit = (arguments["limit"] as? Number)?.toInt()?.coerceIn(1, 20) ?: 5
        return memory.searchMessages(query, limit)
    }
}
