package utils

import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.sql.Connection
import java.sql.DriverManager

data class CronJob(
    val id: Int,
    val prompt: String,
    val intervalMinutes: Int,
    val lastRunAt: Long?,
    val createdAt: String
)

class CronScheduler(
    private val dbPath: String,
    private val inbox: SendChannel<IncomingMessage>,
    private val replyTo: ChatInterface = SelfChatInterface(),
    private val logLevel: LogLevel = LogLevel.INFO
) {
    private val log = Logger.getLogger("CronScheduler", logLevel)
    private var conn: Connection? = null

    fun init() {
        conn = DriverManager.getConnection("jdbc:sqlite:$dbPath")
        conn!!.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS cron_jobs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                prompt TEXT NOT NULL,
                interval_minutes INTEGER NOT NULL,
                last_run_at INTEGER,
                created_at TEXT NOT NULL DEFAULT (datetime('now'))
            )
        """.trimIndent())
        log.info("Cron scheduler initialized")
    }

    suspend fun start() = coroutineScope {
        launch {
            while (true) {
                try {
                    checkAndFire()
                } catch (_: Exception) {}
                delay(30_000L)
            }
        }
    }

    fun addJob(prompt: String, intervalMinutes: Int): String {
        val stmt = conn!!.prepareStatement(
            "INSERT INTO cron_jobs (prompt, interval_minutes, last_run_at) VALUES (?, ?, 0)"
        )
        stmt.setString(1, prompt)
        stmt.setInt(2, intervalMinutes)
        stmt.executeUpdate()
        val id = conn!!.createStatement().executeQuery("SELECT last_insert_rowid()").getInt(1)
        stmt.close()
        log.info("Cron job #$id added: \"$prompt\" every ${intervalMinutes}m")
        return "Cron job #$id added — runs every ${intervalMinutes} minute(s): \"$prompt\""
    }

    fun removeJob(id: Int): String {
        val stmt = conn!!.prepareStatement("DELETE FROM cron_jobs WHERE id = ?")
        stmt.setInt(1, id)
        val count = stmt.executeUpdate()
        stmt.close()
        return if (count > 0) "Cron job #$id removed"
        else "Error: cron job #$id not found"
    }

    fun listJobs(): String {
        val stmt = conn!!.createStatement()
        val rs = stmt.executeQuery("SELECT id, prompt, interval_minutes, last_run_at FROM cron_jobs ORDER BY id")
        val jobs = mutableListOf<String>()
        while (rs.next()) {
            val id = rs.getInt("id")
            val prompt = rs.getString("prompt")
            val interval = rs.getInt("interval_minutes")
            val lastRun = rs.getLong("last_run_at")
            val dueIn = if (lastRun > 0) {
                val next = lastRun + interval * 60_000L
                val remaining = (next - System.currentTimeMillis()) / 60000
                if (remaining > 0) "due in ${remaining}m" else "due now"
            } else "never run"
            jobs.add("#$id: \"$prompt\" every ${interval}m ($dueIn)")
        }
        rs.close()
        stmt.close()
        return if (jobs.isEmpty()) "No cron jobs scheduled"
        else jobs.joinToString("\n")
    }

    private fun checkAndFire() {
        val now = System.currentTimeMillis()
        val stmt = conn!!.createStatement()
        val rs = stmt.executeQuery(
            "SELECT id, prompt, interval_minutes, last_run_at FROM cron_jobs"
        )
        val due = mutableListOf<Pair<Int, String>>()
        while (rs.next()) {
            val lastRun = rs.getLong("last_run_at")
            val interval = rs.getInt("interval_minutes")
            if (lastRun == 0L || (now - lastRun) >= interval * 60_000L) {
                due.add(rs.getInt("id") to rs.getString("prompt"))
            }
        }
        rs.close()
        stmt.close()

        for ((id, prompt) in due) {
            log.info("Firing cron job #$id: \"$prompt\"")
            inbox.trySend(IncomingMessage("[Cron] $prompt", replyTo, "cron"))
            val update = conn!!.prepareStatement("UPDATE cron_jobs SET last_run_at = ? WHERE id = ?")
            update.setLong(1, now)
            update.setInt(2, id)
            update.executeUpdate()
            update.close()
        }
    }

    fun close() {
        conn?.close()
        conn = null
    }
}
