package core

import interfaces.Cli
import interfaces.DiscordBot
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import utils.AppConfig
import tools.BashTool
import tools.GlobTool
import tools.GrepTool
import tools.OpencodeTool
import tools.ReadTool
import tools.ToolRegistry
import tools.WebFetchTool
import tools.WriteTool
import utils.Logger
import utils.MemoryStore

fun main() = runBlocking {
    val config = AppConfig.load()
    val log = Logger.getLogger("Main", config.logLevel)

    log.info("Starting Watney4 — provider: ${config.provider}, log.level: ${config.logLevel.name.lowercase()}")

    val cli = Cli(config.logLevel)
    val discord = DiscordBot(config.discordToken, config.logLevel)
    val inbox = Channel<utils.IncomingMessage>(Channel.UNLIMITED)

    launch { cli.start(inbox) }
    launch { discord.start(inbox) }

    val memory = MemoryStore(config.memoryDbPath)

    val tools = ToolRegistry(listOf(
        ReadTool(),
        WriteTool(),
        BashTool(),
        GlobTool(),
        GrepTool(),
        OpencodeTool(),
        WebFetchTool()
    ))

    val agent = Agent(
        inbox = inbox,
        llm = AppConfig.createProvider(config),
        tools = tools,
        logLevel = config.logLevel,
        memory = memory
    )
    agent.run()

    cli.stop()
    discord.stop()
    log.info("Watney4 stopped")
}
