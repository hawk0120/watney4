package core

import interfaces.Cli
import interfaces.DiscordBot
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import utils.AppConfig
import tools.BashTool
import tools.ContextInjectTool
import tools.ContextStatusTool
import tools.ContextTruncateTool
import tools.SystemStatusTool
import tools.CronTool
import tools.ForgetMemoryTool
import tools.GlobTool
import tools.GrepTool
import tools.MemorySearchTool
import tools.OpencodeTool
import tools.ReadTool
import tools.RemindTool
import tools.SaveMemoryTool
import tools.ToolRegistry
import tools.WebFetchTool
import tools.WebSearchTool
import tools.WriteTool
import utils.CronScheduler
import utils.Context
import utils.HookRegistry
import utils.Logger
import utils.MemoryStore
import utils.ReminderScheduler
import utils.SelfChatInterface
import utils.VoiceChatManager

fun main() = runBlocking {
    val config = AppConfig.load()
    val log = Logger.getLogger("Main", config.logLevel)

    log.info("Starting Watney4 — provider: ${config.provider}, log.level: ${config.logLevel.name.lowercase()}")

    val cli = Cli(config.logLevel)
    val discord = DiscordBot(config.discordToken, config.logLevel)
    val inbox = Channel<utils.IncomingMessage>(Channel.UNLIMITED)

    launch { cli.start(inbox) }
    launch { discord.start(inbox) }

    val voiceChat = VoiceChatManager(inbox, discord, config.logLevel)
    discord.voiceChat = voiceChat

    val memory = MemoryStore(config.memoryDbPath)
    val selfChat = SelfChatInterface()

    val reminderScheduler = ReminderScheduler(inbox, selfChat, this)

    val cronScheduler = CronScheduler(
        dbPath = config.memoryDbPath,
        inbox = inbox,
        replyTo = selfChat,
        logLevel = config.logLevel
    )
    cronScheduler.init()
    launch { cronScheduler.start() }

    val ctx = Context()

    val tools = ToolRegistry(listOf(
        ReadTool(),
        WriteTool(),
        BashTool(),
        GlobTool(),
        GrepTool(),
        OpencodeTool(),
        WebFetchTool(),
        WebSearchTool(),
        CronTool(cronScheduler),
        RemindTool(reminderScheduler),
        MemorySearchTool(memory),
        SaveMemoryTool(memory),
        ForgetMemoryTool(memory),
        ContextTruncateTool(ctx),
        ContextInjectTool(ctx),
        ContextStatusTool(ctx),
        SystemStatusTool()
    ))

    val agent = Agent(
        inbox = inbox,
        llm = AppConfig.createProvider(config),
        tools = tools,
        ctx = ctx,
        logLevel = config.logLevel,
        memory = memory,
        hooks = HookRegistry()
    )
    agent.run()
    voiceChat.leave()
    cronScheduler.close()

    cli.stop()
    discord.stop()
    log.info("Watney4 stopped")
}
