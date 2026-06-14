package core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import utils.ChatMessage
import utils.Context
import utils.IncomingMessage
import utils.LLMProvider
import utils.LLMResult
import utils.LogLevel
import utils.Logger
import utils.MemoryStore
import tools.ToolCall
import tools.ToolRegistry

class Agent(
    private val inbox: ReceiveChannel<IncomingMessage>,
    private val llm: LLMProvider,
    private val tools: ToolRegistry? = null,
    private val ctx: Context = Context(),
    private val persona: Watney4 = Watney4(),
    private val logLevel: LogLevel = LogLevel.INFO,
    private val memory: MemoryStore? = null,
    private val scope: CoroutineScope,
    private val consolidationTimezone: String = "Europe/Berlin",
    private val consolidationHour: Int = 3
) {
    private val log = Logger.getLogger("Agent", logLevel)
    private val messages = mutableListOf<ChatMessage>()
    private var shutdownRequested = false
    private var turnCount = 0
    private val maxToolIterations = 15

    suspend fun run() {
        ctx.bind(messages)
        installShutdownHook()
        memory?.init()
        messages.add(ChatMessage("system", persona.whoAmI()))
        val toolDefs = tools?.definitions()
        if (toolDefs != null && toolDefs.isNotEmpty()) {
            log.info("Agent loop started — ${toolDefs.size} tool(s) registered")
        } else {
            log.info("Agent loop started — no tools registered")
        }

        memory?.let {
            val history = it.loadRecentMessages(50)
            if (history.isNotEmpty()) {
                messages.addAll(history)
                log.info("Loaded ${history.size} messages from history")
            }
            val allMemories = it.loadMemories()
            val autoPrefixes = listOf("Conversation ", "fact:")
            val autoMemories = allMemories.filter { m -> autoPrefixes.any { p -> m.key.startsWith(p) } }
            if (autoMemories.isNotEmpty()) {
                log.info("Found ${autoMemories.size} auto-generated memories in DB (not loaded into context)")
            }
            val regularMemories = allMemories.filter { m -> autoPrefixes.none { p -> m.key.startsWith(p) } }
            if (regularMemories.isNotEmpty()) {
                val memoryText = regularMemories.joinToString("\n") { "- ${it.key}: ${it.content}" }
                messages.add(ChatMessage("system", "Here's what I remember about you:\n$memoryText"))
                log.info("Loaded ${regularMemories.size} memories into context (${allMemories.size} total in DB)")
            }
        }

        scope.launch { dailyConsolidationLoop() }

        while (true) {
            currentCoroutineContext().ensureActive()
            if (shutdownRequested) {
                log.info("Shutdown flag set, exiting loop")
                break
            }

            val msg = inbox.receiveCatching().getOrNull() ?: break
            val trimmed = msg.text.trim()

            if (trimmed.equals("/exit", ignoreCase = true) ||
                trimmed.equals("/quit", ignoreCase = true)
            ) {
                log.info("Exit requested from ${msg.replyTo.label}")
                msg.replyTo.sendMessage("Goodbye.")
                break
            }

            if (trimmed.equals("/clear", ignoreCase = true)) {
                val nonSystemMessages = messages.filter { it.role != "system" }
                val count = nonSystemMessages.size
                messages.removeAll { it.role != "system" }
                turnCount = 0
                log.info("Context cleared from ${msg.replyTo.label} — removed $count messages")
                msg.replyTo.sendMessage("Context cleared. Removed $count messages, system prompt preserved.")

                if (count > 0 && memory != null) {
                    scope.launch { consolidate(nonSystemMessages, saveFacts = true) }
                }

                continue
            }

            val contextText = if (msg.type != "user") "[${msg.type}] $trimmed" else trimmed
            messages.add(ChatMessage("user", contextText))
            memory?.saveMessage("user", contextText)
            log.debug("Input from ${msg.replyTo.label} (${trimmed.length} chars): ${trimmed.take(80)}...")

            val turnStartTime = System.currentTimeMillis()
            val toolsUsedInTurn = mutableListOf<String>()
            val progress = throttledProgress { text -> msg.replyTo.sendMessage(text) }
            var done = false

            for (iter in 1..maxToolIterations) {
                log.trace("Tool loop iteration $iter")

                when (val result = llm.query(messages.toList(), tools?.definitions())) {
                    is LLMResult.Success -> {
                        if (result.calls.isNullOrEmpty()) {
                            val response = result.response
                            messages.add(ChatMessage("assistant", response))
                            memory?.saveMessage("assistant", response)
                            turnCount++
                            val elapsed = System.currentTimeMillis() - turnStartTime
                            log.info("Turn $turnCount complete — ${messages.size} messages in context, ${elapsed}ms")
                            msg.replyTo.sendMessage(response)
                            done = true
                            memory?.logInteraction(
                                turnNumber = turnCount,
                                userMessage = contextText,
                                assistantResponse = response,
                                model = llm.modelName,
                                responseTimeMs = elapsed,
                                toolsUsed = toolsUsedInTurn.joinToString(", ").ifEmpty { null },
                                promptTokens = result.promptTokens,
                                completionTokens = result.completionTokens,
                                totalTokens = result.totalTokens
                            )
                            break
                        }

                        toolsUsedInTurn.addAll(result.calls.map { it.name })
                        if (result.response.isNotBlank()) {
                            msg.replyTo.sendMessage(result.response)
                        }
                        messages.add(ChatMessage("assistant", result.response, toolCalls = result.calls))
                        executeToolCalls(result.calls, progress)
                    }
                    is LLMResult.Error -> {
                        msg.replyTo.sendMessage("Sorry, I encountered an error: ${result.message}")
                        done = true
                        break
                    }
                }
            }

            if (!done) {
                msg.replyTo.sendMessage("I've reached the maximum number of tool calls and couldn't complete my response.")
            }
        }

        memory?.close()
    }

    private suspend fun executeToolCalls(calls: List<ToolCall>, progress: (String) -> Unit) {
        for (call in calls) {
            log.debug("Executing tool: ${call.name}(${call.arguments})")
            val detail = when (call.name) {
                "bash" -> (call.arguments["command"] as? String)?.let { "`$it`" } ?: "running..."
                "write" -> (call.arguments["filePath"] as? String)?.let { "`$it`" } ?: "running..."
                "read" -> (call.arguments["filePath"] as? String)?.let { "`$it`" } ?: "running..."
                "glob" -> (call.arguments["pattern"] as? String)?.let { "`$it`" } ?: "running..."
                "grep" -> (call.arguments["pattern"] as? String)?.let { "`$it`" } ?: "running..."
                "web_search" -> (call.arguments["query"] as? String)?.let { it.take(80) } ?: "running..."
                "web_fetch" -> (call.arguments["url"] as? String)?.let { "`$it`" } ?: "running..."
                "cron" -> (call.arguments["action"] as? String)?.let { it } ?: "running..."
                "memory_search" -> (call.arguments["query"] as? String)?.let { it.take(80) } ?: "running..."
                "save_memory" -> (call.arguments["key"] as? String)?.let { "key=$it" } ?: "running..."
                "forget_memory" -> (call.arguments["key"] as? String)?.let { "key=$it" } ?: "running..."
                "opencode" -> (call.arguments["task"] as? String)?.let { it.take(120) } ?: "running..."
                "context_truncate" -> (call.arguments["keepLast"] as? Double)?.toInt()?.let { "keep last $it" } ?: "running..."
                "context_inject" -> (call.arguments["role"] as? String)?.let { "role=$it" } ?: "running..."
                "context_status" -> "checking..."
                "system_status" -> "checking..."
                else -> "running..."
            }
            progress("**${call.name}** — $detail")
            val toolResult = tools?.execute(call.name, call.arguments, progress)
                ?: "Error: no tools registered"
            log.debug("Tool result: ${toolResult.take(200)}...")
            messages.add(
                ChatMessage(
                    role = "tool",
                    content = toolResult,
                    toolCallId = call.id,
                    name = call.name
                )
            )
        }
    }

    private fun throttledProgress(send: suspend (String) -> Unit): (String) -> Unit {
        val lastSend = AtomicLong(0)
        val buffer = StringBuilder()
        val minIntervalMs = 2000L

        return { text ->
            val now = System.currentTimeMillis()
            synchronized(buffer) { buffer.appendLine(text) }
            if (now - lastSend.get() >= minIntervalMs) {
                lastSend.set(now)
                val batch = synchronized(buffer) {
                    val result = buffer.toString()
                    buffer.clear()
                    result
                }
                kotlinx.coroutines.runBlocking { send(batch) }
            }
        }
    }

    private suspend fun consolidate(nonSystemMessages: List<ChatMessage>, saveFacts: Boolean) {
        withContext(Dispatchers.IO) {
            val conversationText = buildString {
                for (m in nonSystemMessages) {
                    when (m.role) {
                        "user" -> appendLine("USER: ${m.content}")
                        "assistant" -> {
                            appendLine("ASSISTANT: ${m.content}")
                            if (!m.toolCalls.isNullOrEmpty()) {
                                for (tc in m.toolCalls) {
                                    appendLine("  [TOOL CALL: ${tc.name}(${tc.arguments})]")
                                }
                            }
                        }
                        "tool" -> appendLine("  [TOOL RESULT: ${m.content?.take(200)}]")
                        else -> appendLine("${m.role.uppercase()}: ${m.content}")
                    }
                }
            }.take(12000)

            val ts = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

            val summaryResult = llm.query(listOf(
                ChatMessage("system", "You are a conversation summarizer. Summarize the key topics, claims, decisions, preferences, and important information from the following conversation with the user. Be concise but comprehensive. Focus on factual information that would be useful to remember long-term. Do not use any tools."),
                ChatMessage("user", "Summarize this conversation:\n\n$conversationText")
            ))
            if (summaryResult is LLMResult.Success) {
                memory?.saveMemory("Conversation $ts", summaryResult.response)
                log.info("Saved conversation summary to long-term memory")
            }

            if (saveFacts) {
                val factResult = llm.query(listOf(
                    ChatMessage("system", "Extract durable factual claims, preferences, decisions, and personal information about the user from this conversation. Return each as a separate line in format: KEY: VALUE. Keys should be short, descriptive, and reusable. Skip transient/tool-related content. Do not use any tools."),
                    ChatMessage("user", "Extract facts from:\n\n$conversationText")
                ))
                if (factResult is LLMResult.Success) {
                    val facts = factResult.response.lines().mapNotNull { line ->
                        val match = Regex("^([A-Za-z_ ]+): (.+)$").find(line.trim())
                        match?.let { it.groupValues[1].trim() to it.groupValues[2].trim() }
                    }
                    for ((key, value) in facts) {
                        memory?.saveMemory("fact:$key", value)
                    }
                    if (facts.isNotEmpty()) {
                        log.info("Saved ${facts.size} facts to long-term memory")
                    }
                }
            }
        }
    }

    private suspend fun dailyConsolidationLoop() {
        while (true) {
            try {
                val zoneId = java.time.ZoneId.of(consolidationTimezone)
                val now = java.time.ZonedDateTime.now(zoneId)
                var nextRun = now.withHour(consolidationHour).withMinute(0).withSecond(0).withNano(0)
                if (!nextRun.isAfter(now)) {
                    nextRun = nextRun.plusDays(1)
                }
                val delayMs = java.time.Duration.between(now, nextRun).toMillis()
                log.info("Next daily consolidation scheduled at $nextRun (${delayMs / 60000} minutes away)")
                delay(delayMs)

                val nonSystemMessages = messages.filter { it.role != "system" }
                if (nonSystemMessages.isNotEmpty() && memory != null) {
                    log.info("Running daily consolidation — ${nonSystemMessages.size} messages to archive")
                    consolidate(nonSystemMessages, saveFacts = false)
                    messages.removeAll { it.role != "system" }
                    log.info("Daily consolidation complete — removed ${nonSystemMessages.size} messages from context")
                }

                delay(24 * 60 * 60 * 1000L)
            } catch (e: Exception) {
                log.error("Daily consolidation error: ${e.message}")
                delay(60_000L)
            }
        }
    }

    private fun installShutdownHook() {
        Runtime.getRuntime().addShutdownHook(Thread {
            shutdownRequested = true
            log.info("SIGINT received, shutting down...")
        })
    }
}
