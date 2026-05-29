package utils

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class LogLevel(val priority: Int) {
    DEBUG(1), INFO(2), WARN(3), ERROR(4)
}

class Logger(private val name: String, private val minLevel: LogLevel = LogLevel.INFO) {
    private val fmt = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

    fun debug(msg: String) = log(LogLevel.DEBUG, msg)
    fun info(msg: String) = log(LogLevel.INFO, msg)
    fun warn(msg: String) = log(LogLevel.WARN, msg)
    fun error(msg: String) = log(LogLevel.ERROR, msg)

    private fun log(level: LogLevel, msg: String) {
        if (level.priority < minLevel.priority) return
        val ts = fmt.format(Instant.now())
        println("$ts ${level.name.padEnd(5)} [$name] $msg")
    }

    companion object {
        fun getLogger(name: String, level: LogLevel = LogLevel.INFO): Logger = Logger(name, level)
    }
}
