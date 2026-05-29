package utils

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.time.Instant

data class MistralMessage(
    val role: String,
    val content: String
)

data class MistralRequest(
    val model: String,
    val messages: List<MistralMessage>,
    val stream: Boolean = false
)

data class MistralChoice(
    val message: MistralMessage
)

data class MistralResponse(
    val choices: List<MistralChoice>
)

class MistralProvider(
    private val apiKey: String,
    private val model: String = "ministral-8b-2512",
    private val baseUrl: String = "https://api.mistral.ai/v1/chat/completions",
    private val logLevel: LogLevel = LogLevel.INFO
) : LLMProvider {
    private val log = Logger.getLogger("MistralProvider", logLevel)
    private val gson = Gson()

    override suspend fun query(messages: List<ChatMessage>): LLMResult = withContext(Dispatchers.IO) {
        val start = Instant.now()
        log.debug("Querying model=$model, messages=${messages.size}")

        try {
            val mistralMessages = messages.map { MistralMessage(it.role, it.content) }
            val request = MistralRequest(model, mistralMessages)

            val connection = URI(baseUrl).toURL().openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.doOutput = true

            connection.outputStream.bufferedWriter().use { writer ->
                writer.write(gson.toJson(request))
            }

            val rawJson = connection.inputStream.bufferedReader().use { it.readText() }
            val parsed = gson.fromJson(rawJson, MistralResponse::class.java)
            val content = parsed.choices.firstOrNull()?.message?.content
                ?: return@withContext LLMResult.Error("No choices in response")

            val elapsed = java.time.Duration.between(start, Instant.now()).toMillis()
            log.info("Query OK — ${elapsed}ms, response=${content.length} chars, prompt=${messages.last().content.take(60)}...")
            LLMResult.Success(content)
        } catch (e: Exception) {
            val elapsed = java.time.Duration.between(start, Instant.now()).toMillis()
            log.error("Query failed after ${elapsed}ms: ${e.message}")
            LLMResult.Error("Mistral API query failed: ${e.message}")
        }
    }

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val conn = URI(baseUrl).toURL().openConnection() as HttpURLConnection
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.responseCode == 200
        } catch (_: Exception) {
            false
        }
    }
}
