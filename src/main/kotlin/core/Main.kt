package core

import interfaces.Cli
import interfaces.DiscordBot
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import utils.AppConfig
import utils.BashTool
import utils.GlobTool
import utils.GrepTool
import utils.Logger
import utils.MemoryStore
import utils.OpencodeTool
import utils.ReadTool
import utils.ToolRegistry
import utils.WriteTool

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
        OpencodeTool()
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
