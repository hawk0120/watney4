package utils

data class ChatMessage(
    val role: String,
    val content: String
)

sealed class LLMResult {
    data class Success(val response: String) : LLMResult()
    data class Error(val message: String) : LLMResult()
}

interface LLMProvider {
    suspend fun query(messages: List<ChatMessage>): LLMResult
    suspend fun isAvailable(): Boolean
}
