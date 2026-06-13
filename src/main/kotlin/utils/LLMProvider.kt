package utils

import tools.ToolCall

data class ChatMessage(
    val role: String,
    val content: String?,
    val toolCalls: List<ToolCall>? = null,
    val toolCallId: String? = null,
    val name: String? = null
)

sealed class LLMResult {
    data class Success(
        val response: String,
        val calls: List<ToolCall>? = null,
        val promptTokens: Int? = null,
        val completionTokens: Int? = null,
        val totalTokens: Int? = null,
        val elapsedMs: Long = 0
    ) : LLMResult()
    data class Error(val message: String) : LLMResult()
}

interface LLMProvider {
    val modelName: String
    suspend fun query(messages: List<ChatMessage>, tools: List<Map<String, Any>>? = null): LLMResult
    suspend fun isAvailable(): Boolean
}
