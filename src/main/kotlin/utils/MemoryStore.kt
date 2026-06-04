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

    fun close() {
        conn?.close()
        conn = null
    }
}
