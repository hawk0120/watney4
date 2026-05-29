package utils

data class ChatMessage(
    val role: String,
    val content: String?,
    val toolCalls: List<ToolCall>? = null,
    val toolCallId: String? = null,
    val name: String? = null
)

sealed class LLMResult {
    data class Success(val response: String) : LLMResult()
    data class ToolCalls(val calls: List<ToolCall>) : LLMResult()
    data class Error(val message: String) : LLMResult()
}

interface LLMProvider {
    suspend fun query(messages: List<ChatMessage>, tools: List<Map<String, Any>>? = null): LLMResult
    suspend fun isAvailable(): Boolean
}
