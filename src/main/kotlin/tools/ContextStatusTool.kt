package tools

import utils.Context

class ContextStatusTool(private val ctx: Context) : Tool {
    override val name = "context_status"
    override val description = "Check the current conversation context size and composition. Use this before truncating to decide how many messages to keep."
    override val parameters = mapOf(
        "type" to "object",
        "properties" to emptyMap<String, Any>(),
        "required" to emptyList<String>()
    )

    override suspend fun execute(args: Map<String, Any?>, progress: ((String) -> Unit)?): String {
        val total = ctx.size
        val system = ctx.toList().count { it.role == "system" }
        val user = ctx.toList().count { it.role == "user" }
        val assistant = ctx.toList().count { it.role == "assistant" }
        val tool = ctx.toList().count { it.role == "tool" }
        val charCount = ctx.toList().sumOf { it.content?.length ?: 0 }
        return buildString {
            appendLine("**Context Status**")
            appendLine("Total messages: $total")
            appendLine("System: $system | User: $user | Assistant: $assistant | Tool: $tool")
            appendLine("Total characters: $charCount")
            append("Rough token estimate: ${charCount / 4}")
        }
    }
}
