package interfaces

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.withContext
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.requests.GatewayIntent
import utils.ChatInterface
import utils.IncomingMessage
import utils.LogLevel
import utils.Logger

class DiscordBot(
    private val token: String,
    private val logLevel: LogLevel = LogLevel.INFO
) : ChatInterface {
    override val label = "Discord"
    private val log = Logger.getLogger(label, logLevel)
    private var jda: net.dv8tion.jda.api.JDA? = null
    private var lastChannel: PrivateChannel? = null

    override suspend fun start(sink: SendChannel<IncomingMessage>) = withContext(Dispatchers.IO) {
        log.info("Starting Discord bot...")

        jda = JDABuilder.createDefault(token, GatewayIntent.MESSAGE_CONTENT)
            .enableIntents(GatewayIntent.DIRECT_MESSAGES, GatewayIntent.GUILD_MESSAGES)
            .addEventListeners(EventListener { event ->
                when (event) {
                    is MessageReceivedEvent -> {
                        if (event.author.isBot) return@EventListener
                        if (!event.isFromType(ChannelType.PRIVATE)) return@EventListener

                        val channel = event.channel as PrivateChannel
                        lastChannel = channel
                        val text = event.message.contentRaw
                        val author = event.author.name

                        log.info("Message from $author: ${text.take(80)}...")
                        sink.trySend(IncomingMessage(text, this@DiscordBot))
                    }
                }
            })
            .build()
            .awaitReady()

        log.info("Discord bot connected as ${jda!!.selfUser.name}")
    }

    override suspend fun sendMessage(text: String) {
        val channel = lastChannel
        if (channel == null) {
            log.warn("No DM channel to reply to")
            return
        }
        channel.sendMessage(text).queue()
        log.debug("Sent ${text.length} chars to DM")
    }

    override suspend fun stop() {
        log.info("Stopping Discord bot...")
        jda?.shutdown()
    }
}
