package tools

import utils.Context

class ContextInjectTool(private val ctx: Context) : Tool {
    override val name = "context_inject"
    override val description = "Inject a message directly into the conversation context. Use this to insert a summary, reminder, or instruction that the LLM will see in subsequent turns."
    override val parameters = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "role" to mapOf(
                "type" to "string",
                "description" to "Message role: 'system' (instruction), 'assistant' (past response), or 'user' (past query)"
            ),
            "content" to mapOf(
                "type" to "string",
                "description" to "The message content to inject"
            ),
            "index" to mapOf(
                "type" to "number",
                "description" to "Position to insert at (0 = beginning, omit = end). Cannot insert before system messages."
            )
        ),
        "required" to listOf("role", "content")
    )

    override suspend fun execute(args: Map<String, Any?>, progress: ((String) -> Unit)?): String {
        val role = args["role"] as? String ?: return "Error: missing 'role' argument"
        val content = args["content"] as? String ?: return "Error: missing 'content' argument"
        val index = (args["index"] as? Double)?.toInt()
        return ctx.inject(role, content, index)
    }
}
