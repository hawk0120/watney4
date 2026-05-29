# Interfaces

Two user-facing interfaces: Discord DM and terminal CLI. Both implement `ChatInterface` and write to the shared `inbox` channel.

## Discord Bot (`interfaces/DiscordBot.kt`)

Built on JDA 5.2.2. Connects to Discord and handles:

- **DM messages** — text content plus attachment download/description
- **Slash commands** — `/clear`, `/voice`, `/status`, `/join`, `/leave`
- **Voice state tracking** — caches which users are in which voice channels

Sending a response splits text into 1990-character chunks. If `voiceMode` is on, also generates a Piper TTS `.ogg` attachment.

## CLI (`interfaces/Cli.kt`)

Simple stdin/stdout loop on `Dispatchers.IO`. Reads lines from the terminal, sends them to the inbox. Prints responses inline.

## ChatInterface (`utils/ChatInterface.kt`)

```kotlin
interface ChatInterface {
    val label: String
    suspend fun start(sink: SendChannel<IncomingMessage>)
    suspend fun stop()
    suspend fun sendMessage(text: String)
}
```

Both Discord and CLI implement this, letting the agent respond without knowing which interface the message came from. `SelfChatInterface` is a no-op implementation for internal messages (cron, reminders).
