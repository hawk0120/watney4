package tools

import java.net.HttpURLConnection
import java.net.URI

class WebFetchTool : Tool {
    override val name = "webfetch"
    override val description = "Fetch a web page and return its content as text. Useful for reading docs, checking current info, looking up error solutions."
    override val parameters = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "url" to mapOf(
                "type" to "string",
                "description" to "The URL to fetch"
            ),
            "timeout" to mapOf(
                "type" to "number",
                "description" to "Timeout in seconds (default 15, max 60)"
            )
        ),
        "required" to listOf("url")
    )

    override suspend fun execute(args: Map<String, Any?>, progress: ((String) -> Unit)?): String {
        val url = args["url"] as? String ?: return "Error: missing 'url' argument"
        val timeout = ((args["timeout"] as? Double)?.toInt() ?: 15).coerceIn(1, 60) * 1000

        return try {
            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = timeout
            conn.readTimeout = timeout
            conn.setRequestProperty("User-Agent", "Watney4/1.0")
            conn.instanceFollowRedirects = true

            val code = conn.responseCode
            if (code != 200) {
                val error = try { conn.errorStream?.bufferedReader()?.readText()?.take(500) } catch (_: Exception) { null }
                return "Error: HTTP $code${if (error != null) " — $error" else ""}"
            }

            val contentType = conn.contentType ?: ""
            val text = conn.inputStream.bufferedReader().use { it.readText() }

            val truncated = if (text.length > 50000) {
                text.take(50000) + "\n\n... (truncated, ${text.length} total chars)"
            } else text

            "URL: $url\nContent-Type: $contentType\n\n$truncated"
        } catch (e: Exception) {
            "Error fetching $url: ${e.message}"
        }
    }
}
