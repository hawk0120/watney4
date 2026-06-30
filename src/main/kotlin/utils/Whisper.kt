package utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import kotlin.io.path.extension

sealed class WhisperEvent {
    abstract val timestamp: Instant
    data class VaultChange(val path: String, override val timestamp: Instant) : WhisperEvent()
    data class ConversationTopic(
        val keywords: List<String>,
        val turnCount: Int,
        val idleMinutes: Long,
        override val timestamp: Instant
    ) : WhisperEvent()
}

class WhisperBuffer(private val logLevel: LogLevel = LogLevel.INFO) {
    private val log = Logger.getLogger("WhisperBuffer", logLevel)
    private val pending = mutableListOf<WhisperEvent>()
    private val seen = mutableSetOf<Instant>()
    private val maxPending = 5

    fun add(event: WhisperEvent) {
        synchronized(pending) {
            if (event.timestamp in seen) return
            seen.add(event.timestamp)
            pending.add(event)
            if (pending.size > maxPending) pending.removeAt(0)
        }
    }

    fun consume(): String {
        synchronized(pending) {
            if (pending.isEmpty()) return ""
            val events = pending.toList()
            pending.clear()
            return format(events)
        }
    }

    private fun format(events: List<WhisperEvent>): String {
        val parts = events.map { e ->
            when (e) {
                is WhisperEvent.VaultChange -> {
                    val name = e.path.substringAfterLast('/')
                    "vault: $name"
                }
                is WhisperEvent.ConversationTopic -> {
                    val kw = e.keywords.take(3).joinToString(", ")
                    buildString {
                        append("topic: $kw")
                        append(" | ${e.turnCount} turns")
                        if (e.idleMinutes > 5) append(" | ${e.idleMinutes}m idle")
                    }
                }
            }
        }
        return "[whisper] ${parts.joinToString(" | ")}"
    }
}

class VaultWatcher(
    private val vaultPath: String,
    private val buffer: WhisperBuffer,
    private val intervalMinutes: Long = 120,
    private val logLevel: LogLevel = LogLevel.INFO
) {
    private val log = Logger.getLogger("VaultWatcher", logLevel)
    private val excludeDirs = setOf(".git", ".obsidian", ".stfolder", "Musicolet", "05 Excalidraw")
    private val watchedExtensions = setOf("md", "txt")

    fun start(scope: CoroutineScope) {
        scope.launch {
            val fileMap = mutableMapOf<String, FileTime>()
            scanChanges(fileMap)
            while (true) {
                delay(intervalMinutes * 60 * 1000)
                scanChanges(fileMap)
            }
        }
        log.info("Vault watcher started — scanning every ${intervalMinutes}m")
    }

    private fun scanChanges(fileMap: MutableMap<String, FileTime>) {
        try {
            val base = Path.of(vaultPath)
            if (!Files.exists(base)) return

            Files.walk(base).use { stream ->
                stream.filter { Files.isRegularFile(it) }
                    .filter { it.extension in watchedExtensions }
                    .filter { p -> excludeDirs.none { d -> p.toString().contains("/$d/") } }
                    .forEach { path ->
                        val lastMod = Files.getLastModifiedTime(path)
                        val prev = fileMap[path.toString()]
                        if (prev == null) {
                            fileMap[path.toString()] = lastMod
                            buffer.add(
                                WhisperEvent.VaultChange(
                                    path = path.toString().removePrefix(vaultPath).trimStart('/'),
                                    timestamp = Instant.now()
                                )
                            )
                        } else if (lastMod != prev) {
                            fileMap[path.toString()] = lastMod
                            buffer.add(
                                WhisperEvent.VaultChange(
                                    path = path.toString().removePrefix(vaultPath).trimStart('/'),
                                    timestamp = Instant.now()
                                )
                            )
                        }
                    }
            }
        } catch (e: Exception) {
            log.warn("Vault scan failed: ${e.message}")
        }
    }
}

class ConversationWatcher(
    private val buffer: WhisperBuffer,
    private val turnMilestone: Int = 50,
    private val logLevel: LogLevel = LogLevel.INFO
) {
    private val log = Logger.getLogger("ConversationWatcher", logLevel)
    private var lastTurnTime = System.currentTimeMillis()
    private var lastMilestoneFired = 0
    private val stopWords = stopWordSet()

    fun onTurn(turnCount: Int, userMessage: String) {
        val now = System.currentTimeMillis()
        val idleMinutes = (now - lastTurnTime) / 60000
        lastTurnTime = now

        val milestone = (turnCount / turnMilestone) * turnMilestone
        if (milestone > 0 && milestone != lastMilestoneFired) {
            lastMilestoneFired = milestone
            val keywords = extractKeywords(userMessage)
            buffer.add(
                WhisperEvent.ConversationTopic(
                    keywords = keywords,
                    turnCount = turnCount,
                    idleMinutes = idleMinutes,
                    timestamp = Instant.now()
                )
            )
        }
    }

    private fun extractKeywords(text: String): List<String> {
        return text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .split("\\s+".toRegex())
            .filter { it.length > 3 && it !in stopWords }
            .distinct()
            .take(5)
    }

    private fun stopWordSet(): Set<String> {
        return setOf(
            "the", "this", "that", "these", "those", "a", "an", "is", "are", "was",
            "were", "be", "been", "being", "have", "has", "had", "do", "does", "did",
            "will", "would", "could", "should", "may", "might", "shall", "can", "need",
            "to", "of", "in", "for", "on", "with", "at", "by", "from", "as", "into",
            "through", "during", "before", "after", "above", "below", "between", "out",
            "off", "over", "under", "again", "further", "then", "once", "here", "there",
            "when", "where", "why", "how", "all", "each", "every", "both", "few", "more",
            "most", "other", "some", "such", "no", "nor", "not", "only", "own", "same",
            "so", "than", "too", "very", "just", "because", "but", "and", "or", "if",
            "while", "that", "what", "which", "who", "whom", "about", "up", "down",
            "like", "i", "me", "my", "we", "you", "your", "he", "she", "they", "them",
            "their", "hi", "hey", "hello", "yeah", "yes", "no", "ok", "okay", "sure",
            "please", "thanks", "thank", "got", "get", "let", "make", "want", "think",
            "know", "see", "say", "tell", "ask", "use", "look", "feel", "try", "leave",
            "come", "go", "take", "give", "put", "set", "send", "find", "bring", "start",
            "stop", "keep", "hold", "write", "read", "run", "move", "play", "turn",
            "help", "show", "hear", "watch", "actually", "really", "pretty", "quite",
            "maybe", "perhaps", "probably", "basically", "literally", "honestly",
            "definitely", "absolutely", "well", "now", "also", "still", "already",
            "always", "never", "sometimes", "usually", "often", "rarely", "ever",
            "doesn", "don", "didn", "won", "wouldn", "couldn", "shouldn", "isn",
            "aren", "wasn", "weren", "hasn", "haven", "hadn", "needn", "dare",
            "mightn", "must", "mustn", "let", "thing", "things", "stuff", "way",
            "kind", "sort", "type", "bit", "lot", "much", "many", "some", "any"
        )
    }
}
