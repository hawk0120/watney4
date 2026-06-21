package utils

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.time.Instant

data class LlamaCppRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean
)

data class LlamaCppResponse(val response: String)

data class LlamaCppEmbeddingRequest(
    val content: String
)

data class LlamaCppEmbeddingResponse(
    val embedding: List<Double>
)

class LlamaCppProvider(
    private val baseUrl: String = "http://127.0.0.1:8080/completion",
    private val model: String = "gemma4:e2b",
    private val logLevel: LogLevel = LogLevel.INFO,
    private val embeddingBaseUrl: String = "http://127.0.0.1:8080/embedding"
) : LLMProvider {
    override val modelName: String get() = "llamacpp:$model"
    private val log = Logger.getLogger("LlamaCppProvider", logLevel)
    private val gson = Gson()

    override suspend fun query(messages: List<ChatMessage>, tools: List<Map<String, Any>>?): LLMResult = withContext(Dispatchers.IO) {
        val start = Instant.now()
        log.debug("Querying model=$model, messages=${messages.size}")

        try {
            val prompt = messages.joinToString("\n") { "${it.role}: ${it.content ?: ""}" }

            val connection = URI(baseUrl).toURL().openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val request = LlamaCppRequest(model, prompt, false)
            connection.outputStream.bufferedWriter().use { writer ->
                writer.write(gson.toJson(request))
            }

            val rawJson = connection.inputStream.bufferedReader().use { it.readText() }
            val parsed = gson.fromJson(rawJson, LlamaCppResponse::class.java)

            val elapsed = java.time.Duration.between(start, Instant.now()).toMillis()
            log.info("Query OK — ${elapsed}ms, response=${parsed.response.length} chars")
            LLMResult.Success(
                response = parsed.response,
                elapsedMs = elapsed
            )
        } catch (e: Exception) {
            val elapsed = java.time.Duration.between(start, Instant.now()).toMillis()
            log.error("Query failed after ${elapsed}ms: ${e.message}")
            LLMResult.Error("LLM query failed: ${e.message}")
        }
    }

    override suspend fun embed(text: String, model: String?): FloatArray = withContext(Dispatchers.IO) {
        log.debug("Embedding text=${text.take(80)}... with model=$model")

        try {
            val request = LlamaCppEmbeddingRequest(content = text)
            val body = gson.toJson(request)

            val connection = URI(embeddingBaseUrl).toURL().openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.outputStream.bufferedWriter().use { it.write(body) }

            val rawJson = connection.inputStream.bufferedReader().use { it.readText() }

            if (connection.responseCode != 200) {
                log.error("Embedding API error (${connection.responseCode}): ${rawJson.take(200)}")
                throw RuntimeException("llama.cpp embedding error ${connection.responseCode}")
            }

            val parsed = gson.fromJson(rawJson, LlamaCppEmbeddingResponse::class.java)
            val floatArray = FloatArray(parsed.embedding.size) { parsed.embedding[it].toFloat() }
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
            conn.responseCode == 200
        } catch (_: Exception) {
            false
        }
    }
}
