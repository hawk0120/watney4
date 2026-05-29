package core

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import utils.ChatMessage
import utils.IncomingMessage
import utils.LLMProvider
import utils.LLMResult
import utils.LogLevel
import utils.Logger
import utils.MemoryStore

class Agent(
    private val inbox: ReceiveChannel<IncomingMessage>,
    private val llm: LLMProvider,
    private val persona: Watney4 = Watney4(),
    private val logLevel: LogLevel = LogLevel.INFO,
    private val memory: MemoryStore? = null
) {
    private val log = Logger.getLogger("Agent", logLevel)
    private val messages = mutableListOf<ChatMessage>()
    private var shutdownRequested = false
    private var turnCount = 0

    suspend fun run() {
        installShutdownHook()
        memory?.init()
        messages.add(ChatMessage("system", persona.whoAmI()))
        log.info("Agent loop started — ${messages.size} system messages loaded")

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

            messages.add(ChatMessage("user", trimmed))
            memory?.saveMessage("user", trimmed)
            log.debug("Input from ${msg.replyTo.label} (${trimmed.length} chars): ${trimmed.take(80)}...")

            when (val result = llm.query(messages.toList())) {
                is LLMResult.Success -> {
                    turnCount++
                    messages.add(ChatMessage("assistant", result.response))
                    memory?.saveMessage("assistant", result.response)
                    log.info("Turn $turnCount complete — ${messages.size} messages in context")
                    msg.replyTo.sendMessage(result.response)
                }
                is LLMResult.Error -> {
                    log.error("LLM query failed: ${result.message}")
                    msg.replyTo.sendMessage("Sorry, I encountered an error.")
                }
            }
        }

        memory?.close()
    }

    private fun installShutdownHook() {
        Runtime.getRuntime().addShutdownHook(Thread {
            shutdownRequested = true
            log.info("SIGINT received, shutting down...")
        })
    }
}
