package tools

class WebSearchTool : Tool {
    override val name = "websearch"
    override val description = "Search the web using DuckDuckGo. Returns titles, URLs, and snippets."
    override val parameters = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "query" to mapOf(
                "type" to "string",
                "description" to "Search query"
            ),
            "maxResults" to mapOf(
                "type" to "number",
                "description" to "Maximum results (default 5, max 10)"
            )
        ),
        "required" to listOf("query")
    )

    override suspend fun execute(args: Map<String, Any?>, progress: ((String) -> Unit)?): String {
        val query = args["query"] as? String ?: return "Error: missing 'query' argument"
        val maxResults = ((args["maxResults"] as? Double)?.toInt() ?: 5).coerceIn(1, 10)
        val safe = query.replace("'", "'\\''")

        return try {
            val script = """
                from ddgs import DDGS
                results = DDGS().text('$safe', max_results=$maxResults)
                for r in results:
                    print(r.get('title', ''))
                    print(r.get('href', ''))
                    print(r.get('body', ''))
                    print('---')
            """.trimIndent()

            val process = ProcessBuilder("python3", "-c", script)
                .redirectErrorStream(true)
                .start()

            val finished = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return "Error: search timed out"
            }

            val output = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.exitValue()
            if (exitCode != 0 || output.isBlank()) {
                return "Error: no results found (exit code $exitCode)"
            }

            val lines = output.lines()
            val results = mutableListOf<String>()
            var i = 0
            while (i + 2 < lines.size) {
                val title = lines[i].trim()
                val url = lines[i + 1].trim()
                val snippet = lines[i + 2].trim()
                if (title.isNotBlank()) {
                    results.add("$title\n  URL: $url\n  $snippet")
                }
                i += 4
            }

            if (results.isEmpty()) return "No results found for '$query'"
            results.joinToString("\n\n")
        } catch (e: Exception) {
            "Error searching: ${e.message}"
        }
    }
}
