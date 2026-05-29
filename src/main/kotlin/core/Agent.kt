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
import utils.ToolRegistry

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

            val finalResponse = runToolLoop(throttledProgress { text ->
                msg.replyTo.sendMessage(text)
            })

            messages.add(ChatMessage("assistant", finalResponse))
            memory?.saveMessage("assistant", finalResponse)
            turnCount++
            log.info("Turn $turnCount complete — ${messages.size} messages in context")
            msg.replyTo.sendMessage(finalResponse)
        }

        memory?.close()
    }

    private suspend fun runToolLoop(progress: (String) -> Unit): String {
        var iterations = 0
        while (iterations < maxToolIterations) {
            iterations++
            log.trace("Tool loop iteration $iterations")

            when (val result = llm.query(messages.toList(), tools?.definitions())) {
                is LLMResult.Success -> return result.response
                is LLMResult.ToolCalls -> {
                    for (call in result.calls) {
                        log.debug("Executing tool: ${call.name}(${call.arguments})")
                        progress("**${call.name}** — running...")
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
                is LLMResult.Error -> return "Sorry, I encountered an error: ${result.message}"
            }
        }
        return "I've reached the maximum number of tool calls and couldn't complete my response."
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
