package utils

import tools.ToolRegistry
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ResearchSession(
    private val llm: LLMProvider,
    private val tools: ToolRegistry,
    private val discord: ChatInterface? = null,
    private val logLevel: LogLevel = LogLevel.INFO
) {
    private val log = Logger.getLogger("ResearchSession", logLevel)
    private val maxIterations = 20
    private val vaultPath = "/home/hawk0120/Documents/obsidian/07 Lab/Research"

    suspend fun run(topic: String) {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm"))
        val slug = topic.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40)
        val filename = "$slug-$timestamp.md"
        val notePath = "$vaultPath/$filename"

        Files.createDirectories(Path.of(vaultPath))

        val header = buildString {
            appendLine("# Research: $topic")
            appendLine()
            appendLine("Started: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}")
            appendLine("Max iterations: $maxIterations")
            appendLine()
            appendLine("---")
            appendLine()
        }
        Files.writeString(Path.of(notePath), header)
        log.info("Research session started: $topic -> $filename")
        discord?.sendMessage("**Research started:** $topic — logging to `07 Lab/Research/$filename`")

        val messages = mutableListOf<ChatMessage>()
        messages.add(ChatMessage("system", """
            You are in autonomous research mode. Your goal is to investigate this topic thoroughly:

            $topic

            You have access to web_search, web_fetch, read, bash, and other tools. Each iteration, decide what to do next based on what you've learned so far. Run multiple searches from different angles, read sources, cross-reference, and build a complete picture.

            When you have enough information, write a comprehensive summary of everything you found and end with [RESEARCH COMPLETE].

            Rules:
            - Be thorough — explore multiple angles and sources
            - Cross-reference information from different sources
            - If you hit dead ends, try different search terms or approaches
            - Your entire conversation is being logged, so keep thinking out loud
            - End with [RESEARCH COMPLETE] only when you have a solid understanding
        """.trimIndent()))

        var logContent = header

        for (iteration in 1..maxIterations) {
            log.info("Research iteration $iteration/$maxIterations")

            when (val result = llm.query(messages.toList(), tools.definitions())) {
                is LLMResult.Success -> {
                    val entry = buildString {
                        appendLine("## Iteration $iteration — ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))}")
                        appendLine()
                        if (result.response.isNotBlank()) {
                            appendLine(result.response)
                            appendLine()
                        }
                    }
                    logContent += entry
                    writeNote(notePath, logContent)

                    if (!result.calls.isNullOrEmpty()) {
                        messages.add(ChatMessage("assistant", result.response, toolCalls = result.calls))

                        for (call in result.calls) {
                            log.debug("Research tool call: ${call.name}")
                            val toolResult = tools.execute(call.name, call.arguments)

                            logContent += buildString {
                                appendLine("**Tool: ${call.name}**")
                                appendLine("Arguments: ${call.arguments}")
                                appendLine("```")
                                appendLine(toolResult.take(2000))
                                if (toolResult.length > 2000) appendLine("...[truncated]")
                                appendLine("```")
                                appendLine()
                            }
                            writeNote(notePath, logContent)

                            messages.add(ChatMessage("tool", toolResult, toolCallId = call.id, name = call.name))
                        }
                    } else {
                        messages.add(ChatMessage("assistant", result.response))

                        if (result.response.contains("[RESEARCH COMPLETE]")) {
                            logContent += "\n**Research complete after $iteration iterations.**\n"
                            writeNote(notePath, logContent)
                            log.info("Research completed: $topic ($iteration iterations)")
                            discord?.sendMessage("**Research complete:** $topic — $iteration iterations. See `07 Lab/Research/$filename`")
                            return
                        }

                        messages.add(ChatMessage("user", "Continue researching. Use tools to go deeper. When satisfied, write your summary and end with [RESEARCH COMPLETE]."))
                    }
                }
                is LLMResult.Error -> {
                    val err = "Error: ${result.message}"
                    logContent += "## Error\n$err\n"
                    writeNote(notePath, logContent)
                    log.error("Research error: ${result.message}")
                    discord?.sendMessage("**Research error:** $topic — ${result.message}")
                    return
                }
            }
        }

        logContent += "\n**Research ended — reached $maxIterations iteration limit.**\n"
        writeNote(notePath, logContent)
        log.info("Research limit reached: $topic ($maxIterations iterations)")
        discord?.sendMessage("**Research limit reached:** $topic — $maxIterations iterations used. See `07 Lab/Research/$filename`")
    }

    private fun writeNote(path: String, content: String) {
        Files.writeString(Path.of(path), content)
    }
}
