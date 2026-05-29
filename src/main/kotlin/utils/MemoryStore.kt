package utils

import java.sql.Connection
import java.sql.DriverManager

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

    fun close() {
        conn?.close()
        conn = null
    }
}
