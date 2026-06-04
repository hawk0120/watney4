package tools

import utils.Context

class ContextTruncateTool(private val ctx: Context) : Tool {
    override val name = "context_truncate"
    override val description = "Truncate old conversation history, keeping only the most recent messages. Use this when the conversation is getting long and you need to free up context space. The system prompt is always preserved."
    override val parameters = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "keepLast" to mapOf(
                "type" to "number",
                "description" to "Number of most recent non-system messages to keep (minimum 1)"
            )
        ),
        "required" to listOf("keepLast")
    )

    override suspend fun execute(args: Map<String, Any?>, progress: ((String) -> Unit)?): String {
        val keep = ((args["keepLast"] as? Double)?.toInt() ?: return "Error: missing or invalid 'keepLast' argument")
            .coerceAtLeast(1)
        return ctx.truncate(keep)
    }
}
