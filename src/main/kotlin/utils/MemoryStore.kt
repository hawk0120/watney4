package utils

import java.sql.Connection
import java.sql.DriverManager

data class MemoryEntry(
    val key: String,
    val content: String
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
            CREATE TABLE IF NOT EXISTS memories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                key TEXT UNIQUE NOT NULL,
                content TEXT NOT NULL,
                created_at TEXT NOT NULL DEFAULT (datetime('now')),
                updated_at TEXT NOT NULL DEFAULT (datetime('now'))
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

    fun saveMemory(key: String, content: String) {
        val stmt = conn!!.prepareStatement(
            """
            INSERT INTO memories (key, content, updated_at) VALUES (?, ?, datetime('now'))
            ON CONFLICT(key) DO UPDATE SET content = excluded.content, updated_at = datetime('now')
            """
        )
        stmt.setString(1, key)
        stmt.setString(2, content)
        stmt.executeUpdate()
        stmt.close()
    }

    fun loadMemories(): List<MemoryEntry> {
        val stmt = conn!!.prepareStatement(
            "SELECT key, content FROM memories ORDER BY updated_at DESC"
        )
        val rs = stmt.executeQuery()
        val results = mutableListOf<MemoryEntry>()
        while (rs.next()) {
            results.add(MemoryEntry(rs.getString("key"), rs.getString("content")))
        }
        rs.close()
        stmt.close()
        return results
    }

    fun deleteMemory(key: String): Boolean {
        val stmt = conn!!.prepareStatement("DELETE FROM memories WHERE key = ?")
        stmt.setString(1, key)
        val rows = stmt.executeUpdate()
        stmt.close()
        return rows > 0
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
}
