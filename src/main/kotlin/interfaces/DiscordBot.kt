package interfaces

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.withContext
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.FileUpload
import net.dv8tion.jda.api.utils.MemberCachePolicy
import utils.ChatInterface
import utils.IncomingMessage
import utils.LogLevel
import utils.Logger
import utils.TtsGenerator
import utils.VoiceChatManager
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class DiscordBot(
    private val token: String,
    private val logLevel: LogLevel = LogLevel.INFO
) : ChatInterface {
    override val label = "Discord"
    private val log = Logger.getLogger(label, logLevel)
    private var jda: net.dv8tion.jda.api.JDA? = null
    private var lastChannel: PrivateChannel? = null
    private var voiceMode = false
    private val tts = TtsGenerator()
    private val startTime = System.currentTimeMillis()
    private var discordMsgCount = 0
    var voiceChat: VoiceChatManager? = null
    private val userVoiceChannels = mutableMapOf<String, AudioChannel>()

    override suspend fun start(sink: SendChannel<IncomingMessage>) = withContext(Dispatchers.IO) {
        log.info("Starting Discord bot...")

        jda = JDABuilder.createDefault(token)
            .enableIntents(
                GatewayIntent.MESSAGE_CONTENT,
                GatewayIntent.GUILD_MEMBERS,
                GatewayIntent.GUILD_VOICE_STATES
            )
            .setMemberCachePolicy(MemberCachePolicy.ALL)
            .addEventListeners(EventListener { event ->
                when (event) {
                    is ReadyEvent -> {
                        event.jda.updateCommands().addCommands(
                            Commands.slash("clear", "Clear conversation history"),
                            Commands.slash("voice", "Toggle voice mode (TTS responses)"),
                            Commands.slash("status", "Show bot status (uptime, memory, stats)"),
                            Commands.slash("join", "Join your current voice channel"),
                            Commands.slash("leave", "Leave the voice channel")
                        ).queue(
                            { log.info("Slash commands registered (${it.size} commands)") },
                            { log.error("Failed to register slash commands: ${it.message}") }
                        )
                    }
                    is MessageReceivedEvent -> {
                        if (event.author.isBot) return@EventListener
                        if (!event.isFromType(ChannelType.PRIVATE)) return@EventListener

                        discordMsgCount++
                        val channel = event.channel as PrivateChannel
                        lastChannel = channel
                        val author = event.author.name

                        var text = event.message.contentRaw
                        val attachments = event.message.attachments
                        if (attachments.isNotEmpty()) {
                            log.info("Message from $author with ${attachments.size} attachment(s)")
                            val parts = mutableListOf(text)
                            for (a in attachments) parts.add(describeAttachment(a))
                            text = parts.joinToString("\n")
                        }

                        log.info("Message from $author: ${text.take(80)}...")
                        sink.trySend(IncomingMessage(text, this@DiscordBot))
                    }
                    is GuildVoiceUpdateEvent -> {
                        if (event.member.id == event.jda.selfUser.id) return@EventListener
                        val ch = event.channelJoined
                        val member = event.member
                        if (ch != null) {
                            userVoiceChannels[member.id] = ch
                            log.debug("Voice state cache: ${member.effectiveName} -> ${ch.name}")
                        } else {
                            userVoiceChannels.remove(member.id)
                            log.debug("Voice state cache: ${member.effectiveName} removed")
                        }
                    }
                    is SlashCommandInteractionEvent -> {
                        val channel = event.getChannel()
                        if (channel is PrivateChannel) lastChannel = channel

                        when (event.name) {
                            "clear" -> {
                                log.info("Slash command /clear from ${event.user.name}")
                                event.reply("Context cleared. Starting fresh.").setEphemeral(false).queue()
                                sink.trySend(IncomingMessage("/clear", this@DiscordBot))
                            }
                            "voice" -> {
                                voiceMode = !voiceMode
                                val s = if (voiceMode) "on" else "off"
                                log.info("Voice mode toggled $s by ${event.user.name}")
                                event.reply("Voice mode **$s**. I'll ${if (voiceMode) "read my responses aloud" else "respond with text only"}.").setEphemeral(false).queue()
                            }
                            "status" -> {
                                val uptime = System.currentTimeMillis() - startTime
                                val days = uptime / 86400000
                                val hours = (uptime % 86400000) / 3600000
                                val mins = (uptime % 3600000) / 60000
                                val secs = (uptime % 60000) / 1000
                                val rt = Runtime.getRuntime()
                                val usedMem = (rt.totalMemory() - rt.freeMemory()) / 1048576
                                val totalMem = rt.totalMemory() / 1048576
                                val maxMem = rt.maxMemory() / 1048576
                                val vs = if (voiceMode) "on" else "off"
                                val jv = System.getProperty("java.version")
                                val sb = StringBuilder()
                                sb.appendLine("**Watney4 Status**")
                                sb.appendLine("Uptime: ${days}d ${hours}h ${mins}m ${secs}s")
                                sb.appendLine("Discord messages: $discordMsgCount")
                                sb.appendLine("Voice mode: $vs")
                                sb.appendLine("Voice connected: ${voiceChat?.isInVoice == true}")
                                sb.appendLine("Heap: ${usedMem}MB / ${totalMem}MB (max ${maxMem}MB)")
                                sb.append("JVM: $jv")
                                log.info("Status requested by ${event.user.name}")
                                event.reply(sb.toString()).setEphemeral(false).queue()
                            }
                            "join" -> {
                                event.deferReply().queue()
                                val vc = findUserVoiceChannel(event)
                                if (vc != null) {
                                    voiceChat?.join(vc)
                                    event.hook.sendMessage("Joined **${vc.name}** in **${vc.guild.name}**").queue()
                                    log.info("Joined voice channel ${vc.name} for ${event.user.name}")
                                } else {
                                    event.hook.sendMessage("I can't find you in a voice channel. Make sure we share a server and you're in a voice channel.").queue()
                                }
                            }
                            "leave" -> {
                                if (voiceChat?.isInVoice == true) {
                                    voiceChat?.leave()
                                    event.reply("Left the voice channel.").queue()
                                    log.info("Left voice channel for ${event.user.name}")
                                } else {
                                    event.reply("I'm not in a voice channel.").setEphemeral(true).queue()
                                }
                            }
                        }
                    }
                }
            })
            .build()
            .awaitReady()

        for (guild in jda!!.guilds) {
            for (vs in guild.voiceStates) {
                val ch = vs.channel
                if (ch != null) {
                    userVoiceChannels[vs.member.id] = ch
                    log.debug("Voice state cache init: ${vs.member.effectiveName} (${vs.member.id}) in ${ch.name}")
                }
            }
            log.info("Voice state cache initialized: ${guild.voiceStates.size} member(s) in voice in ${guild.name}")
        }

        log.info("Discord bot connected as ${jda!!.selfUser.name}")
    }

    private fun findUserVoiceChannel(event: SlashCommandInteractionEvent): AudioChannel? {
        event.member?.voiceState?.channel?.let {
            log.debug("Found voice channel from event.member: ${it.name}")
            return it
        }

        val user = event.user
        log.debug("Cache keys: ${userVoiceChannels.keys.joinToString()}")
        log.debug("Looking up user.id=${user.id} (${user.name}) in voice state cache (${userVoiceChannels.size} entries)")
        val cached = userVoiceChannels[user.id]
        if (cached != null) {
            log.debug("Found ${user.name} in ${cached.name} from voice state cache")
            return cached
        }

        val guilds = event.jda.guilds
        log.debug("Cache miss. Searching ${guilds.size} guild(s) for ${user.name}'s voice channel")
        for (guild in guilds) {
            log.debug("Checking guild: ${guild.name}")

            val member = guild.getMember(user)
                ?: try {
                    log.debug("Member not cached for ${guild.name}, retrieving via API...")
                    guild.retrieveMember(user).complete()
                } catch (e: Exception) {
                    log.debug("retrieveMember failed for ${guild.name}: ${e.message}")
                    null
                }
                ?: continue

            log.debug("Checking ${guild.voiceChannels.size} voice channel(s) for ${user.name}")
            for (vc in guild.voiceChannels) {
                if (vc.members.any { it.id == member.id }) {
                    log.info("Found ${user.name} in voice channel ${vc.name} via channel members")
                    return vc
                }
            }

            log.debug("Not in any voice channel in ${guild.name}, checking stage channels...")
            for (sc in guild.stageChannels) {
                if (sc.members.any { it.id == member.id }) {
                    log.info("Found ${user.name} in stage channel ${sc.name}")
                    return sc
                }
            }
        }
        log.warn("Could not find ${user.name} in any voice channel across ${guilds.size} guild(s)")
        return null
    }

    override suspend fun sendMessage(text: String) {
        voiceChat?.speak(text)
        val channel = lastChannel
        if (channel == null) {
            log.warn("No DM channel to reply to")
            return
        }
        val chunks = if (text.length > 1990) {
            log.warn("Splitting message of ${text.length} chars into multiple messages")
            splitIntoChunks(text, 1990)
        } else listOf(text)
        chunks.forEach { chunk ->
            channel.sendMessage(chunk).queue()
        }
        log.debug("Sent ${text.length} chars across ${chunks.size} message(s) to DM")

        if (voiceMode) {
            val ttsText = text.take(tts.maxChars)
            val audio = if (ttsText.length < text.length) {
                tts.generate("$ttsText... (response truncated for speech)")
            } else {
                tts.generate(ttsText)
            }
            if (audio != null) {
                channel.sendFiles(FileUpload.fromData(audio, "response.ogg")).queue()
                log.debug("Sent voice response (${audio.size} bytes)")
            } else {
                log.warn("TTS generation failed")
            }
        }
    }

    private fun splitIntoChunks(text: String, maxLen: Int): List<String> {
        val chunks = mutableListOf<String>()
        var remaining = text
        while (remaining.isNotEmpty()) {
            if (remaining.length <= maxLen) {
                chunks.add(remaining)
                break
            }
            var splitAt = remaining.lastIndexOf('\n', maxLen)
            if (splitAt < 1) splitAt = remaining.lastIndexOf(' ', maxLen)
            if (splitAt < 1) splitAt = maxLen
            chunks.add(remaining.substring(0, splitAt))
            remaining = remaining.substring(splitAt).trimStart()
        }
        return chunks
    }

    private fun describeAttachment(a: net.dv8tion.jda.api.entities.Message.Attachment): String {
        val name = a.fileName
        val size = a.size
        val type = a.contentType ?: "unknown"
        val isText = type.startsWith("text/") || name.endsWith(".kt") || name.endsWith(".kts") ||
            name.endsWith(".gradle") || name.endsWith(".properties") || name.endsWith(".json") ||
            name.endsWith(".xml") || name.endsWith(".yml") || name.endsWith(".yaml") ||
            name.endsWith(".toml") || name.endsWith(".md") || name.endsWith(".txt") ||
            name.endsWith(".sh") || name.endsWith(".py") || name.endsWith(".js") ||
            name.endsWith(".ts") || name.endsWith(".css") || name.endsWith(".html") ||
            name.endsWith(".csv") || name.endsWith(".log")

        if (isText && size <= 100_000) {
            try {
                val stream = a.proxy.download().get(15, TimeUnit.SECONDS)
                val content = String(stream.readAllBytes(), StandardCharsets.UTF_8)
                stream.close()
                return "[Attachment: $name ($size bytes)]\n```\n${content.take(3000)}\n```"
            } catch (e: Exception) {
                log.warn("Failed to download attachment $name: ${e.message}")
            }
        }

        return if (a.isImage) "[Attachment: $name ($size bytes, image)]"
        else "[Attachment: $name ($size bytes)]"
    }

    override suspend fun stop() {
        log.info("Stopping Discord bot...")
        jda?.shutdown()
    }
}
