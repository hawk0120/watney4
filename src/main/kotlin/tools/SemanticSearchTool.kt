package tools

import utils.LTMemoryManager

class SemanticSearchTool(private val ltm: LTMemoryManager) : Tool {
    override val name = "semantic_search"
    override val description = "Search archived long-term memories by meaning, not just exact keywords. Use this to recall information from past weeks or sessions based on topic, theme, or conceptual similarity. Results include a similarity score."
    override val parameters = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "query" to mapOf(
                "type" to "string",
                "description" to "Describe what you're looking for naturally — the search finds conceptually related memories"
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
        return ltm.semanticSearch(query, limit)
    }
}
