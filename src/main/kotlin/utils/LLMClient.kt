package hawk0120

import com.google.gson.Gson
import org.springframework.stereotype.Service
import java.net.HttpURLConnection
import java.io.*
import java.net.URL

data class LLMRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean
)

data class LLMResponse(val response: String)

@Service
class LLMClient {
    private val gson = Gson()

    fun query(prompt: String): String {
        val url = URL("http://localhost:11434/api/generate")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true

        val request = LLMRequest("gemma3:1b", prompt, false)
        val requestBody = gson.toJson(request)

        OutputStreamWriter(connection.outputStream).use { it.write(requestBody) }

        val raw = connection.inputStream.bufferedReader().use { it.readText() }
        return extractResponse(raw)
    }

    private fun extractResponse(rawJson: String): String {
        return try {
            val parsed = gson.fromJson(rawJson, LLMResponse::class.java)
            parsed.response
        } catch (e: Exception) {
            rawJson
        }
    }
}

