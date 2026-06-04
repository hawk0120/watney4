package core

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.concurrent.atomic.AtomicLong
import utils.ChatMessage
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
    private val persona: Watney4 = Watney4(),
    private val logLevel: LogLevel = LogLevel.INFO,
    private val memory: MemoryStore? = null
) {
    private val log = Logger.getLogger("Agent", logLevel)
    private val messages = mutableListOf<ChatMessage>()
    private var shutdownRequested = false
    private var turnCount = 0
    private val maxToolIterations = 10

    suspend fun run() {
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
            val memories = it.loadMemories()
            if (memories.isNotEmpty()) {
                val memoryText = memories.joinToString("\n") { "- ${it.key}: ${it.content}" }
                messages.add(ChatMessage("system", "Here's what I remember about you:\n$memoryText"))
                log.info("Loaded ${memories.size} memories into context")
            }
        }

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
                val count = messages.size - 1
                messages.removeAll { it.role != "system" }
                turnCount = 0
                log.info("Context cleared from ${msg.replyTo.label} — removed $count messages")
                msg.replyTo.sendMessage("Context cleared. Removed $count messages, system prompt preserved.")
                continue
            }

            messages.add(ChatMessage("user", trimmed))
            memory?.saveMessage("user", trimmed)
            log.debug("Input from ${msg.replyTo.label} (${trimmed.length} chars): ${trimmed.take(80)}...")

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
                            log.info("Turn $turnCount complete — ${messages.size} messages in context")
                            msg.replyTo.sendMessage(response)
                            done = true
                            break
                        }

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
                "opencode" -> (call.arguments["task"] as? String)?.let { it.take(120) } ?: "running..."
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

    private fun installShutdownHook() {
        Runtime.getRuntime().addShutdownHook(Thread {
            shutdownRequested = true
            log.info("SIGINT received, shutting down...")
        })
    }
}
