package utils

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import tools.ToolCall
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.time.Instant

data class OpenRouterFunctionCall(
    val name: String,
    val arguments: String
)

data class OpenRouterToolCall(
    val id: String,
    val type: String = "function",
    val function: OpenRouterFunctionCall
)

data class OpenRouterMessage(
    val role: String,
    val content: String?,
    val tool_calls: List<OpenRouterToolCall>? = null,
    val tool_call_id: String? = null,
    val name: String? = null
)

data class OpenRouterRequest(
    val model: String,
    val messages: List<OpenRouterMessage>,
    val tools: List<Map<String, Any>>? = null,
    val stream: Boolean = false
)

data class OpenRouterChoice(
    val index: Int = 0,
    val finish_reason: String? = null,
    val message: OpenRouterMessage
)

data class OpenRouterUsage(
    val prompt_tokens: Int = 0,
    val completion_tokens: Int = 0,
    val total_tokens: Int = 0
)

data class OpenRouterResponse(
    val id: String? = null,
    val choices: List<OpenRouterChoice>,
    val usage: OpenRouterUsage? = null
)

data class OpenRouterError(
    val message: String? = null,
    val type: String? = null,
    val code: Int? = null
)

data class OpenRouterErrorResponse(
    val error: OpenRouterError? = null
)

data class OpenRouterEmbeddingRequest(
    val model: String,
    val input: String
)

data class OpenRouterEmbeddingData(
    val embedding: List<Double>,
    val index: Int = 0
)

data class OpenRouterEmbeddingResponse(
    val data: List<OpenRouterEmbeddingData>,
    val model: String? = null,
    val usage: Map<String, Int>? = null
)

class OpenRouterProvider(
    private val apiKey: String,
    private val model: String = "google/gemma-4-31b-it:free",
    private val baseUrl: String = "https://openrouter.ai/api/v1/chat/completions",
    private val logLevel: LogLevel = LogLevel.INFO,
    private val embeddingModel: String = "openai/text-embedding-3-small",
    private val embeddingBaseUrl: String = "https://openrouter.ai/api/v1/embeddings"
) : LLMProvider {
    override val modelName: String get() = "openrouter:$model"
    private val log = Logger.getLogger("OpenRouterProvider", logLevel)
    private val gson = Gson()

    override suspend fun query(
        messages: List<ChatMessage>,
        tools: List<Map<String, Any>>?
    ): LLMResult = withContext(Dispatchers.IO) {
        val start = Instant.now()
        log.debug("Querying model=$model, messages=${messages.size}, tools=${tools?.size ?: 0}")

        try {
            val openrouterMessages = messages.map { msg ->
                OpenRouterMessage(
                    role = msg.role,
                    content = msg.content,
                    tool_calls = msg.toolCalls?.map { tc ->
                        OpenRouterToolCall(
                            id = tc.id,
                            function = OpenRouterFunctionCall(tc.name, gson.toJson(tc.arguments))
                        )
                    },
                    tool_call_id = msg.toolCallId,
                    name = msg.name
                )
            }

            val request = OpenRouterRequest(
                model = model,
                messages = openrouterMessages,
                tools = tools?.ifEmpty { null }
            )

            val body = gson.toJson(request)
            log.trace("Request body: ${body.take(500)}...")

            val connection = URI(baseUrl).toURL().openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("HTTP-Referer", "https://github.com/bhawkins93/watney4")
            connection.setRequestProperty("X-Title", "Watney4")
            connection.doOutput = true

            connection.outputStream.bufferedWriter().use { writer ->
                writer.write(body)
            }

            val rawJson = connection.inputStream.bufferedReader().use { it.readText() }
            val elapsed = java.time.Duration.between(start, Instant.now()).toMillis()

            if (connection.responseCode != 200) {
                val errorResponse = try {
                    gson.fromJson(rawJson, OpenRouterErrorResponse::class.java)
                } catch (_: Exception) {
                    null
                }
                val errorMsg = errorResponse?.error?.message ?: rawJson.take(200)
                val errorCode = errorResponse?.error?.code
                log.error("API error ($errorCode, ${connection.responseCode}): $errorMsg")
                return@withContext LLMResult.Error("OpenRouter API error ${connection.responseCode}: $errorMsg")
            }

            val parsed = gson.fromJson(rawJson, OpenRouterResponse::class.java)
            val choice = parsed.choices.firstOrNull()
                ?: return@withContext LLMResult.Error("No choices in response")

            val finishReason = choice.finish_reason
            val message = choice.message

            val content = message.content ?: ""
            val calls = if (message.tool_calls != null && message.tool_calls.isNotEmpty()) {
                message.tool_calls.map { tc ->
                    val argsType = object : TypeToken<Map<String, Any?>>() {}.type
                    val parsedArgs: Map<String, Any?> = try {
                        gson.fromJson(tc.function.arguments, argsType)
                    } catch (_: Exception) {
                        mapOf("raw" to tc.function.arguments)
                    }
                    ToolCall(id = tc.id, name = tc.function.name, arguments = parsedArgs)
                }
            } else null

            if (calls != null) {
                log.info("Query OK — ${elapsed}ms, response=${content.length} chars, ${calls.size} tool call(s), finish_reason=$finishReason")
            } else {
                log.info("Query OK — ${elapsed}ms, response=${content.length} chars, finish_reason=$finishReason")
            }
            LLMResult.Success(
                response = content,
                calls = calls?.ifEmpty { null },
                promptTokens = parsed.usage?.prompt_tokens,
                completionTokens = parsed.usage?.completion_tokens,
                totalTokens = parsed.usage?.total_tokens,
                elapsedMs = elapsed
            )
        } catch (e: Exception) {
            val elapsed = java.time.Duration.between(start, Instant.now()).toMillis()
            log.error("Query failed after ${elapsed}ms: ${e.message}")
            LLMResult.Error("OpenRouter API query failed: ${e.message}")
        }
    }

    override suspend fun embed(text: String, model: String?): FloatArray = withContext(Dispatchers.IO) {
        val embedModel = model ?: embeddingModel
        log.debug("Embedding text=${text.take(80)}... with model=$embedModel")

        try {
            val request = OpenRouterEmbeddingRequest(model = embedModel, input = text)
            val body = gson.toJson(request)

            val connection = URI(embeddingBaseUrl).toURL().openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("HTTP-Referer", "https://github.com/bhawkins93/watney4")
            connection.setRequestProperty("X-Title", "Watney4")
            connection.doOutput = true
            connection.outputStream.bufferedWriter().use { it.write(body) }

            val rawJson = connection.inputStream.bufferedReader().use { it.readText() }

            if (connection.responseCode != 200) {
                val errorResponse = try {
                    gson.fromJson(rawJson, OpenRouterErrorResponse::class.java)
                } catch (_: Exception) { null }
                val errorMsg = errorResponse?.error?.message ?: rawJson.take(200)
                log.error("Embedding API error (${connection.responseCode}): $errorMsg")
                throw RuntimeException("OpenRouter embedding error ${connection.responseCode}: $errorMsg")
            }

            val parsed = gson.fromJson(rawJson, OpenRouterEmbeddingResponse::class.java)
            val embedding = parsed.data.firstOrNull()?.embedding
                ?: throw RuntimeException("No embedding data in response")

            val floatArray = FloatArray(embedding.size) { embedding[it].toFloat() }
            log.debug("Embedding OK — ${floatArray.size} dimensions")
            floatArray
        } catch (e: Exception) {
            log.error("Embedding failed: ${e.message}")
            throw e
        }
    }

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val conn = URI(baseUrl).toURL().openConnection() as HttpURLConnection
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("HTTP-Referer", "https://github.com/bhawkins93/watney4")
            conn.setRequestProperty("X-Title", "Watney4")
            conn.responseCode == 200
        } catch (_: Exception) {
            false
        }
    }
}
