package tools

import java.util.concurrent.TimeUnit

class SystemStatusTool : Tool {
    override val name = "system_status"
    override val description = "Check system health: CPU load, RAM usage, disk usage, uptime, and running services. Use the 'full' scope for detailed info including top processes and service list."
    override val parameters = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "scope" to mapOf(
                "type" to "string",
                "description" to "'quick' (default) for summary, 'full' for detailed breakdown including services and top processes"
            )
        ),
        "required" to emptyList<String>()
    )

    override suspend fun execute(args: Map<String, Any?>, progress: ((String) -> Unit)?): String {
        val full = (args["scope"] as? String)?.lowercase() == "full"

        return buildString {
            appendLine("**System Status**")
            appendLine()

            run("uptime -p")?.let { append("**Uptime:** ").appendLine(it.trim().removePrefix("up ")) }

            val cores = run("nproc")
            val load = run("cat /proc/loadavg")
            if (load != null) {
                val parts = load.trim().split(" ")
                val loads = parts.take(3).joinToString(" / ")
                append("**CPU:** ${cores?.trim() ?: "?"} cores, load: $loads")
                appendLine()
            }

            val mem = run("free -h")
            if (mem != null) {
                val lines = mem.lines()
                val memLine = lines.find { it.startsWith("Mem:") }
                if (memLine != null) {
                    val cols = memLine.split(Regex("\\s+"))
                    if (cols.size >= 3) {
                        val total = cols[1]
                        val used = cols[2]
                        val avail = cols.last()
                        val usedPct = try {
                            val usedVal = parseSize(cols[2])
                            val totalVal = parseSize(cols[1])
                            if (totalVal > 0) " (${(usedVal * 100 / totalVal).toInt()}%)" else ""
                        } catch (_: Exception) { "" }
                        append("**Memory:** ${used} used / ${total} total$usedPct — ${avail} available")
                        appendLine()
                    }
                }
                val swapLine = lines.find { it.startsWith("Swap:") }
                if (swapLine != null) {
                    val cols = swapLine.split(Regex("\\s+"))
                    if (cols.size >= 3 && cols[1] != "0B") {
                        append("**Swap:** ${cols[2]} used / ${cols[1]} total")
                        appendLine()
                    }
                }
            }

            val disk = run("df -h --exclude-type=tmpfs --exclude-type=devtmpfs --exclude-type=squashfs 2>/dev/null || df -h")
            if (disk != null) {
                val diskLines = disk.lines().drop(1).filter { it.isNotBlank() }
                if (diskLines.isNotEmpty()) {
                    appendLine("**Disk:**")
                    for (line in diskLines) {
                        val cols = line.split(Regex("\\s+"))
                        if (cols.size >= 6) {
                            appendLine("  ${cols[5]}  ${cols[2]} used / ${cols[1]} total (${cols[4]})")
                        }
                    }
                }
            }

            if (full) {
                appendLine()
                val services = run("systemctl list-units --type=service --state=running --no-pager 2>/dev/null")
                if (services != null) {
                    val serviceLines = services.lines().drop(1).filter {
                        it.isNotBlank() && !it.contains("LOAD") && !it.contains("● ─")
                    }.take(30)
                    if (serviceLines.isNotEmpty()) {
                        appendLine("**Running Services:**")
                        val names = serviceLines.mapNotNull { line ->
                            val parts = line.split(Regex("\\s+"))
                            parts.firstOrNull()?.removeSuffix(".service")
                        }
                        for (name in names) {
                            appendLine("  • $name")
                        }
                        if (serviceLines.size > 30) appendLine("  ... and ${serviceLines.size - 30} more")
                    } else {
                        append("**Running services:** ")
                        val count = run("systemctl list-units --type=service --state=running --no-pager 2>/dev/null | grep -c '.service'")
                        appendLine(count?.trim() ?: "?")
                    }
                } else {
                    val count = run("systemctl list-units --type=service --state=running --no-pager 2>/dev/null | grep -c '.service'")
                    append("**Running services:** ")
                    appendLine(count?.trim() ?: "?")
                }

                appendLine()
                val processes = run("ps aux --sort=-%mem 2>/dev/null | head -10")
                if (processes != null) {
                    val procLines = processes.lines()
                    if (procLines.size > 1) {
                        appendLine("**Top Processes (by memory):**")
                        for (line in procLines.drop(1)) {
                            val cols = line.split(Regex("\\s+"), 11)
                            if (cols.size >= 11) {
                                val user = cols[0]
                                val cpu = cols[2]
                                val memPct = cols[3]
                                val cmd = cols[10].take(60)
                                appendLine("  $cmd  ${cpu}%CPU ${memPct}%MEM ($user)")
                            }
                        }
                    }
                }
            } else {
                val svcCount = run("systemctl list-units --type=service --state=running --no-pager 2>/dev/null | grep -c '.service'")
                append("**Running services:** ")
                appendLine(svcCount?.trim() ?: "?")
            }
        }
    }

    private fun parseSize(s: String): Long {
        val num = s.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: return 0
        return when {
            s.endsWith("Ti") -> (num * 1024 * 1024 * 1024 * 1024).toLong()
            s.endsWith("Gi") -> (num * 1024 * 1024 * 1024).toLong()
            s.endsWith("Mi") -> (num * 1024 * 1024).toLong()
            s.endsWith("Ki") -> (num * 1024).toLong()
            s.endsWith("T") -> (num * 1024 * 1024 * 1024 * 1024).toLong()
            s.endsWith("G") -> (num * 1024 * 1024 * 1024).toLong()
            s.endsWith("M") -> (num * 1024 * 1024).toLong()
            s.endsWith("K") -> (num * 1024).toLong()
            else -> num.toLong()
        }
    }

    private fun run(command: String): String? {
        return try {
            val process = ProcessBuilder("bash", "-c", command)
                .redirectErrorStream(true)
                .start()
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                null
            } else {
                val out = process.inputStream.bufferedReader().readText().trim()
                if (out.isEmpty() || process.exitValue() != 0) null else out
            }
        } catch (_: Exception) {
            null
        }
    }
}
