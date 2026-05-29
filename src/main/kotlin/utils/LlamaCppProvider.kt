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

class LlamaCppProvider(
    private val baseUrl: String = "http://127.0.0.1:8080/completion",
    private val model: String = "gemma4:e2b",
    private val logLevel: LogLevel = LogLevel.INFO
) : LLMProvider {
    private val log = Logger.getLogger("LlamaCppProvider", logLevel)
    private val gson = Gson()

    override suspend fun query(messages: List<ChatMessage>): LLMResult = withContext(Dispatchers.IO) {
        val start = Instant.now()
        log.debug("Querying model=$model, messages=${messages.size}")

        try {
            val prompt = messages.joinToString("\n") { "${it.role}: ${it.content}" }

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
            LLMResult.Success(parsed.response)
        } catch (e: Exception) {
            val elapsed = java.time.Duration.between(start, Instant.now()).toMillis()
            log.error("Query failed after ${elapsed}ms: ${e.message}")
            LLMResult.Error("LLM query failed: ${e.message}")
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
