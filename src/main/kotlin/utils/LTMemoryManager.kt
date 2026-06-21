package utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.sqrt

class LTMemoryManager(
    private val memory: MemoryStore,
    private val llm: LLMProvider,
    private val weekStartDay: DayOfWeek = DayOfWeek.MONDAY
) {
    private val log = Logger.getLogger("LTMemoryManager")

    private val perspectives = listOf(
        "factual" to "Extract verifiable facts, dates, numbers, decisions, and concrete information from the following conversation summary. Be precise and specific. Return only the factual summary, no preamble.",
        "preferences" to "Extract the user's stated or implied preferences, likes, dislikes, and personal tastes from the following conversation summary. Return only the preferences summary, no preamble.",
        "projects" to "Extract all project-related information from the following conversation summary: status updates, plans, technical decisions, dependencies, and blockers. Return only the project summary, no preamble.",
        "goals" to "Extract goals, milestones, aspirations, and long-term plans mentioned in the following conversation summary. Return only the goals summary, no preamble.",
        "personal" to "Extract personal context from the following conversation summary: relationships, habits, routines, health, lifestyle information. Return only the personal summary, no preamble."
    )

    private val refusalPatterns = listOf(
        "i cannot", "i'm unable", "i am unable", "i apologize",
        "i cannot", "i can't", "no information", "nothing",
        "does not contain", "no content"
    )

    fun getCurrentWeekStart(): String {
        val today = LocalDate.now()
        val monday = today.with(TemporalAdjusters.previousOrSame(weekStartDay))
        return monday.toString()
    }

    fun addSessionSummary(content: String, metadata: String = "{}"): Long {
        val weekStart = getCurrentWeekStart()
        val id = memory.saveLTMEntry(weekStart, content, metadata)
        log.info("Saved session summary to LTM (week=$weekStart, id=$id, ${content.length} chars)")
        return id
    }

    suspend fun loadCurrentWeekMemories(): String = withContext(Dispatchers.IO) {
        val weekStart = getCurrentWeekStart()
        val entries = memory.getLTMEntries(weekStart)
        if (entries.isEmpty()) return@withContext ""

        buildString {
            appendLine("## Current Week LTM ($weekStart)")
            entries.forEachIndexed { i, entry ->
                appendLine("\n### Session ${i + 1} (${entry.createdAt})")
                appendLine(entry.content.take(1000))
                val summaries = memory.getLTMSummaries(entry.id)
                if (summaries.isNotEmpty()) {
                    appendLine("\nPerspectives:")
                    summaries.forEach { (perspective, summary) ->
                        appendLine("- $perspective: ${summary.take(300)}")
                    }
                }
            }
        }
    }

    suspend fun semanticSearch(query: String, limit: Int = 5): String = withContext(Dispatchers.IO) {
        log.info("Semantic search: query='${query.take(80)}', limit=$limit")

        val queryEmbedding = try {
            llm.embed(query)
        } catch (e: Exception) {
            log.error("Embedding failed for query: ${e.message}")
            return@withContext "Error generating embedding for query: ${e.message}"
        }

        val archiveEntries = memory.loadArchiveWithEmbeddings()
        if (archiveEntries.isEmpty()) {
            return@withContext "No archived memories found to search."
        }

        val scored = archiveEntries.map { (id, bytes, content) ->
            val entryEmbedding = MemoryStore.bytesToEmbedding(bytes)
            val similarity = cosineSimilarity(queryEmbedding, entryEmbedding)
            Triple(id, similarity, content)
        }.sortedByDescending { it.second }.take(limit)

        val results = scored.map { (id, score, content) ->
            val entry = memory.getArchiveEntry(id)
            val perspective = entry?.perspective?.let { " [$it]" } ?: ""
            val tags = entry?.semanticTags?.let { "\nTags: ${it.take(200)}" } ?: ""
            val week = entry?.weekStart?.let { "\nWeek: $it" } ?: ""
            "[similarity: ${"%.3f".format(score)}]$perspective$week\n${content.take(500)}$tags"
        }

        results.joinToString("\n\n---\n\n").let { result ->
            if (result.isBlank()) "No semantically similar memories found."
            else result
        }
    }

    suspend fun rotateWeek() {
        val currentWeek = getCurrentWeekStart()
        val weeks = memory.getDistinctWeeks()
        val oldWeek = weeks.firstOrNull { it != currentWeek } ?: run {
            log.info("No previous week to rotate")
            return
        }

        log.info("Rotating LTM: archiving week $oldWeek")

        val entries = memory.getLTMEntries(oldWeek)
        var archived = 0

        for (entry in entries) {
            try {
                val summaries = generatePerspectives(entry.content)
                val tags = generateSemanticTags(entry.content)

                for ((perspective, summary) in summaries) {
                    val embedding = try {
                        val raw = llm.embed(summary)
                        MemoryStore.embeddingToBytes(raw)
                    } catch (e: Exception) {
                        log.warn("Embedding failed for entry ${entry.id}/$perspective: ${e.message}")
                        null
                    }

                    memory.saveToArchive(
                        originalEntryId = entry.id,
                        weekStart = oldWeek,
                        content = summary,
                        perspective = perspective,
                        semanticTags = tags,
                        embedding = embedding,
                        metadata = entry.metadata
                    )
                }
                archived++
            } catch (e: Exception) {
                log.error("Failed to archive entry ${entry.id}: ${e.message}")
            }
        }

        memory.deleteLTMEntries(oldWeek)
        log.info("Archived $archived/${entries.size} LTM entries from week $oldWeek")

        val weekHighlight = try {
            generateWeekHighlight(oldWeek)
        } catch (e: Exception) {
            log.warn("Week highlight generation failed: ${e.message}")
            null
        }

        if (weekHighlight != null) {
            memory.saveLTMEntry(currentWeek, "[Week Recap: $oldWeek] $weekHighlight", """{"type": "week_recap"}""")
            log.info("Saved week highlight for $oldWeek into new week")
        }
    }

    private suspend fun generatePerspectives(content: String): List<Pair<String, String>> {
        return perspectives.map { (name, instruction) ->
            val summary = generateWithRetry(instruction, content, name, maxRetries = 3)
            name to summary
        }
    }

    private suspend fun generateWithRetry(
        instruction: String,
        content: String,
        label: String,
        maxRetries: Int
    ): String {
        for (attempt in 0 until maxRetries) {
            val prompt = if (attempt == 0) instruction
            else "$instruction\n\nThe previous attempt produced an invalid response. Provide a comprehensive and complete summary. Do not refuse or hedge."

            val result = llm.query(listOf(
                ChatMessage("system", prompt),
                ChatMessage("user", content)
            ))

            val response = when (result) {
                is LLMResult.Success -> result.response.trim()
                is LLMResult.Error -> ""
            }

            if (isValidSummary(response)) {
                return response
            }

            log.warn("$label summary invalid on attempt ${attempt + 1} (len=${response.length}), retrying")
        }

        log.warn("$label summary failed after $maxRetries attempts, using fallback")
        return "[Failed to generate $label summary after $maxRetries attempts]"
    }

    private fun isValidSummary(text: String): Boolean {
        if (text.isBlank() || text.length < 20) return false
        val lower = text.lowercase()
        return refusalPatterns.none { lower.contains(it) }
    }

    private suspend fun generateSemanticTags(content: String): String = withContext(Dispatchers.IO) {
        val result = llm.query(listOf(
            ChatMessage("system", "Generate 5-10 semantic search keywords or short phrases that capture the key topics, entities, and themes in the following text. Return them as a comma-separated list. Do not use any tools."),
            ChatMessage("user", content)
        ))

        when (result) {
            is LLMResult.Success -> result.response.trim()
            is LLMResult.Error -> ""
        }
    }

    private suspend fun generateWeekHighlight(oldWeek: String): String? = withContext(Dispatchers.IO) {
        val summaries = memory.getLTMEntries(oldWeek)
        if (summaries.isEmpty()) return@withContext null

        val combined = summaries.joinToString("\n---\n") { it.content }

        val result = llm.query(listOf(
            ChatMessage("system", "You are a week-in-review summarizer. Create a concise highlight of the key events, decisions, and developments from this week's conversations. Focus on what's most important to remember going forward. Do not use any tools."),
            ChatMessage("user", "Summarize this week ($oldWeek):\n\n$combined")
        ))

        when (result) {
            is LLMResult.Success -> result.response.trim()
            is LLMResult.Error -> null
        }
    }

    companion object {
        fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
            var dotProduct = 0f
            var normA = 0f
            var normB = 0f
            for (i in a.indices) {
                dotProduct += a[i] * b[i]
                normA += a[i] * a[i]
                normB += b[i] * b[i]
            }
            val denom = sqrt(normA) * sqrt(normB)
            return if (denom == 0f) 0f else dotProduct / denom
        }
    }
}
