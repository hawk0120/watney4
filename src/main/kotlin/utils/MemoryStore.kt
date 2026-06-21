package utils

import java.sql.Connection
import java.sql.DriverManager
import java.nio.ByteBuffer

data class LTMEntry(
    val id: Long,
    val weekStart: String,
    val content: String,
    val metadata: String = "{}",
    val createdAt: String = ""
)

data class ArchiveEntry(
    val id: Long,
    val originalEntryId: Long?,
    val weekStart: String,
    val content: String,
    val perspective: String?,
    val semanticTags: String?,
    val metadata: String = "{}",
    val archivedAt: String = ""
)

class MemoryStore(private val dbPath: String) {
    private var conn: Connection? = null

    fun init() {
        Class.forName("org.sqlite.JDBC")
        conn = DriverManager.getConnection("jdbc:sqlite:$dbPath")
        conn!!.createStatement().execute(
            """
            CREATE TABLE IF NOT EXISTS messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                created_at TEXT NOT NULL DEFAULT (datetime('now'))
            )
            """.trimIndent()
        )
        conn!!.createStatement().execute(
            """
            CREATE TABLE IF NOT EXISTS ltm_entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                week_start TEXT NOT NULL,
                content TEXT NOT NULL,
                metadata TEXT NOT NULL DEFAULT '{}',
                created_at TEXT NOT NULL DEFAULT (datetime('now'))
            )
            """.trimIndent()
        )
        conn!!.createStatement().execute(
            """
            CREATE TABLE IF NOT EXISTS ltm_summaries (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                entry_id INTEGER NOT NULL,
                perspective TEXT NOT NULL,
                content TEXT NOT NULL,
                created_at TEXT NOT NULL DEFAULT (datetime('now')),
                FOREIGN KEY (entry_id) REFERENCES ltm_entries(id)
            )
            """.trimIndent()
        )
        conn!!.createStatement().execute(
            """
            CREATE TABLE IF NOT EXISTS ltm_archive (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                original_entry_id INTEGER,
                week_start TEXT NOT NULL,
                content TEXT NOT NULL,
                perspective TEXT,
                semantic_tags TEXT,
                embedding BLOB,
                metadata TEXT NOT NULL DEFAULT '{}',
                archived_at TEXT NOT NULL DEFAULT (datetime('now'))
            )
            """.trimIndent()
        )
        conn!!.createStatement().execute(
            """
            CREATE TABLE IF NOT EXISTS interaction_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp TEXT NOT NULL DEFAULT (datetime('now')),
                turn_number INTEGER NOT NULL,
                user_message TEXT NOT NULL,
                assistant_response TEXT,
                model TEXT,
                response_time_ms INTEGER,
                tools_used TEXT,
                prompt_tokens INTEGER,
                completion_tokens INTEGER,
                total_tokens INTEGER,
                experiment_mode TEXT
            )
            """.trimIndent()
        )
    }

    fun saveMessage(role: String, content: String) {
        val stmt = conn!!.prepareStatement("INSERT INTO messages (role, content) VALUES (?, ?)")
        stmt.setString(1, role)
        stmt.setString(2, content)
        stmt.executeUpdate()
        stmt.close()
    }

    fun loadRecentMessages(limit: Int = 50): List<ChatMessage> {
        val stmt = conn!!.prepareStatement(
            "SELECT role, content FROM messages ORDER BY id ASC"
        )
        val rs = stmt.executeQuery()
        val all = mutableListOf<ChatMessage>()
        while (rs.next()) {
            all.add(ChatMessage(rs.getString("role"), rs.getString("content")))
        }
        rs.close()
        stmt.close()
        return all.takeLast(limit)
    }

    fun searchMessages(query: String, limit: Int = 10): String {
        val stmt = conn!!.prepareStatement(
            "SELECT role, content, created_at FROM messages WHERE content LIKE ? ORDER BY id DESC LIMIT ?"
        )
        stmt.setString(1, "%$query%")
        stmt.setInt(2, limit)
        val rs = stmt.executeQuery()
        val results = mutableListOf<String>()
        while (rs.next()) {
            val role = rs.getString("role")
            val content = rs.getString("content").take(300)
            val time = rs.getString("created_at")
            results.add("[$time] $role: $content")
        }
        rs.close()
        stmt.close()
        return if (results.isEmpty()) "No messages matching '$query' found"
        else results.joinToString("\n\n")
    }

    fun saveLTMEntry(weekStart: String, content: String, metadata: String = "{}"): Long {
        val stmt = conn!!.prepareStatement(
            "INSERT INTO ltm_entries (week_start, content, metadata) VALUES (?, ?, ?)"
        )
        stmt.setString(1, weekStart)
        stmt.setString(2, content)
        stmt.setString(3, metadata)
        stmt.executeUpdate()
        stmt.close()
        val idStmt = conn!!.createStatement()
        val rs = idStmt.executeQuery("SELECT last_insert_rowid()")
        val id = if (rs.next()) rs.getLong(1) else -1L
        rs.close()
        idStmt.close()
        return id
    }

    fun getLTMEntries(weekStart: String): List<LTMEntry> {
        val stmt = conn!!.prepareStatement(
            "SELECT id, week_start, content, metadata, created_at FROM ltm_entries WHERE week_start = ? ORDER BY created_at ASC"
        )
        stmt.setString(1, weekStart)
        val rs = stmt.executeQuery()
        val results = mutableListOf<LTMEntry>()
        while (rs.next()) {
            results.add(
                LTMEntry(
                    id = rs.getLong("id"),
                    weekStart = rs.getString("week_start"),
                    content = rs.getString("content"),
                    metadata = rs.getString("metadata"),
                    createdAt = rs.getString("created_at")
                )
            )
        }
        rs.close()
        stmt.close()
        return results
    }

    fun getDistinctWeeks(): List<String> {
        val stmt = conn!!.prepareStatement(
            "SELECT DISTINCT week_start FROM ltm_entries ORDER BY week_start DESC"
        )
        val rs = stmt.executeQuery()
        val weeks = mutableListOf<String>()
        while (rs.next()) weeks.add(rs.getString("week_start"))
        rs.close()
        stmt.close()
        return weeks
    }

    fun deleteLTMEntries(weekStart: String) {
        val stmt = conn!!.prepareStatement("DELETE FROM ltm_entries WHERE week_start = ?")
        stmt.setString(1, weekStart)
        stmt.executeUpdate()
        stmt.close()
    }

    fun saveLTMSummary(entryId: Long, perspective: String, content: String): Long {
        val stmt = conn!!.prepareStatement(
            "INSERT INTO ltm_summaries (entry_id, perspective, content) VALUES (?, ?, ?)"
        )
        stmt.setLong(1, entryId)
        stmt.setString(2, perspective)
        stmt.setString(3, content)
        stmt.executeUpdate()
        stmt.close()
        val idStmt = conn!!.createStatement()
        val rs = idStmt.executeQuery("SELECT last_insert_rowid()")
        val id = if (rs.next()) rs.getLong(1) else -1L
        rs.close()
        idStmt.close()
        return id
    }

    fun getLTMSummaries(entryId: Long): List<Pair<String, String>> {
        val stmt = conn!!.prepareStatement(
            "SELECT perspective, content FROM ltm_summaries WHERE entry_id = ? ORDER BY id ASC"
        )
        stmt.setLong(1, entryId)
        val rs = stmt.executeQuery()
        val results = mutableListOf<Pair<String, String>>()
        while (rs.next()) {
            results.add(rs.getString("perspective") to rs.getString("content"))
        }
        rs.close()
        stmt.close()
        return results
    }

    fun saveToArchive(
        originalEntryId: Long?,
        weekStart: String,
        content: String,
        perspective: String?,
        semanticTags: String?,
        embedding: ByteArray?,
        metadata: String = "{}"
    ): Long {
        val stmt = conn!!.prepareStatement(
            "INSERT INTO ltm_archive (original_entry_id, week_start, content, perspective, semantic_tags, embedding, metadata) VALUES (?, ?, ?, ?, ?, ?, ?)"
        )
        if (originalEntryId != null) stmt.setLong(1, originalEntryId) else stmt.setNull(1, java.sql.Types.INTEGER)
        stmt.setString(2, weekStart)
        stmt.setString(3, content)
        if (perspective != null) stmt.setString(4, perspective) else stmt.setNull(4, java.sql.Types.VARCHAR)
        if (semanticTags != null) stmt.setString(5, semanticTags) else stmt.setNull(5, java.sql.Types.VARCHAR)
        if (embedding != null) stmt.setBytes(6, embedding) else stmt.setNull(6, java.sql.Types.BLOB)
        stmt.setString(7, metadata)
        stmt.executeUpdate()
        stmt.close()
        val idStmt = conn!!.createStatement()
        val rs = idStmt.executeQuery("SELECT last_insert_rowid()")
        val id = if (rs.next()) rs.getLong(1) else -1L
        rs.close()
        idStmt.close()
        return id
    }

    fun loadArchiveWithEmbeddings(): List<Triple<Long, ByteArray, String>> {
        val stmt = conn!!.prepareStatement(
            "SELECT id, embedding, content FROM ltm_archive WHERE embedding IS NOT NULL ORDER BY archived_at DESC"
        )
        val rs = stmt.executeQuery()
        val results = mutableListOf<Triple<Long, ByteArray, String>>()
        while (rs.next()) {
            val bytes = rs.getBytes("embedding")
            if (bytes != null) {
                results.add(Triple(rs.getLong("id"), bytes, rs.getString("content")))
            }
        }
        rs.close()
        stmt.close()
        return results
    }

    fun getArchiveEntry(id: Long): ArchiveEntry? {
        val stmt = conn!!.prepareStatement(
            "SELECT id, original_entry_id, week_start, content, perspective, semantic_tags, metadata, archived_at FROM ltm_archive WHERE id = ?"
        )
        stmt.setLong(1, id)
        val rs = stmt.executeQuery()
        val entry = if (rs.next()) {
            ArchiveEntry(
                id = rs.getLong("id"),
                originalEntryId = rs.getObject("original_entry_id") as? Long,
                weekStart = rs.getString("week_start"),
                content = rs.getString("content"),
                perspective = rs.getString("perspective"),
                semanticTags = rs.getString("semantic_tags"),
                metadata = rs.getString("metadata"),
                archivedAt = rs.getString("archived_at")
            )
        } else null
        rs.close()
        stmt.close()
        return entry
    }

    fun logInteraction(
        turnNumber: Int,
        userMessage: String,
        assistantResponse: String?,
        model: String?,
        responseTimeMs: Long,
        toolsUsed: String?,
        promptTokens: Int?,
        completionTokens: Int?,
        totalTokens: Int?,
        experimentMode: String? = null
    ) {
        val stmt = conn!!.prepareStatement(
            """
            INSERT INTO interaction_log 
                (turn_number, user_message, assistant_response, model, response_time_ms, 
                 tools_used, prompt_tokens, completion_tokens, total_tokens, experiment_mode)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        )
        stmt.setInt(1, turnNumber)
        stmt.setString(2, userMessage)
        if (assistantResponse != null) stmt.setString(3, assistantResponse) else stmt.setNull(3, java.sql.Types.VARCHAR)
        if (model != null) stmt.setString(4, model) else stmt.setNull(4, java.sql.Types.VARCHAR)
        stmt.setLong(5, responseTimeMs)
        if (toolsUsed != null) stmt.setString(6, toolsUsed) else stmt.setNull(6, java.sql.Types.VARCHAR)
        if (promptTokens != null) stmt.setInt(7, promptTokens) else stmt.setNull(7, java.sql.Types.INTEGER)
        if (completionTokens != null) stmt.setInt(8, completionTokens) else stmt.setNull(8, java.sql.Types.INTEGER)
        if (totalTokens != null) stmt.setInt(9, totalTokens) else stmt.setNull(9, java.sql.Types.INTEGER)
        if (experimentMode != null) stmt.setString(10, experimentMode) else stmt.setNull(10, java.sql.Types.VARCHAR)
        stmt.executeUpdate()
        stmt.close()
    }

    fun close() {
        conn?.close()
        conn = null
    }

    companion object {
        fun embeddingToBytes(embedding: FloatArray): ByteArray {
            val buffer = ByteBuffer.allocate(embedding.size * 4)
            embedding.forEach { buffer.putFloat(it) }
            return buffer.array()
        }

        fun bytesToEmbedding(bytes: ByteArray): FloatArray {
            val buffer = ByteBuffer.wrap(bytes)
            return FloatArray(bytes.size / 4) { buffer.getFloat() }
        }
    }
}
