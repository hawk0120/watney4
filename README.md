# Watney4

My personal AI assistant. Runs on my homelab, talks through Discord DMs and the terminal. Powered by Mistral AI with tool calling, SQLite memory, voice chat, and local TTS.

This is the fourth agent I've built — Thomas, Flash, Martha, now Watney. Each one got closer to what I actually wanted. This one is the first I'd call production grade.

## Architecture

Two input interfaces (CLI + Discord) push messages into a shared coroutine channel. The agent reads from it, calls the LLM, runs tool calls in a loop, and sends responses back through whatever interface the message came from.

```
CLI  ─┐
       ├─► Channel ──► Agent ──► LLM (Mistral / llama.cpp)
Discord┘                  │
                          ├─► Tools (read, write, bash, search, ...)
                          └─► MemoryStore (SQLite)
```

The voice chat path is separate — audio comes in through Discord's voice API, gets transcribed with Vosk, and the text gets injected into the same channel.

## Why build this?

There are plenty of agents out there. None of them are tuned to how I think, and none of them I'd trust with full system access. I'm not interested in giving more conversation data to big tech. I'd rather own my stack end to end.

Think of it like a chisel — put it in a mason's hands and he makes something beautiful. Put the same chisel in my hands and you'll get something different. The tool amplifies the person. This one amplifies me.

## Features

### Interfaces
- **CLI** — stdin/stdout. Simple.
- **Discord Bot** — DMs only. Slash commands for voice, status, and clearing context.
- **Voice Chat** — join a voice channel, speak, bot transcribes and responds. Full duplex.

### Slash Commands
| Command | What it does |
|---------|-------------|
| `/clear` | Reset conversation context |
| `/voice` | Toggle TTS (.ogg file in DM) |
| `/status` | Uptime, message count, heap, JVM version |
| `/join` | Join your current voice channel |
| `/leave` | Leave the voice channel |

### LLM Providers
- **Mistral AI** — cloud API with full function calling
- **llama.cpp** — local, no tool support yet

### Tools
The LLM can call these to actually do things:

| Tool | What it does |
|------|-------------|
| `read` | Read any file |
| `write` | Write content to any file |
| `bash` | Run shell commands (30s timeout, dangerous stuff blocked) |
| `glob` | Find files by name pattern |
| `grep` | Search file contents by regex |
| `web_search` | DuckDuckGo search |
| `web_fetch` | HTTP GET a URL |
| `cron` | Schedule recurring tasks |
| `memory_search` | Search past conversations |
| `save_memory` | Remember something across sessions |
| `forget_memory` | Delete a saved memory |
| `opencode` | Delegate complex multi-step coding |
| `context_truncate` | Trim old messages when context gets long |
| `context_inject` | Insert a summary or reminder into context |
| `context_status` | Check current context size and composition |
| `system_status` | Check server CPU, RAM, disk, uptime, running services |

### Persistence
- SQLite for conversation history (loads last 50 messages on startup) and cron jobs
- Memory search across all stored messages

### Voice
- **Speech-to-text** — Vosk (offline, ~40MB model, downloads automatically on first use)
- **Text-to-speech** — Piper, played directly into Discord voice channel

### TTS (File-based)
- Piper → WAV → ffmpeg → .ogg, sent as a Discord attachment
- Toggle with `/voice`
- Max ~30 seconds of speech

## Requirements

- Java 21 (GraalVM or OpenJDK)
- Gradle (wrapper included)
- Python 3.11+
- Linux (Ubuntu/Debian-based)

### Quick start
```bash
./setup.sh
```

This installs system packages, Python deps, Piper TTS, creates a config template, and runs the build.

### Python packages
```bash
pip3 install vosk ddgs --break-system-packages
```

### System packages
```bash
sudo apt install ffmpeg opus-tools
```

## Setup

### 1. Create `src/main/resources/application.properties`

```properties
log.level=info

app.provider=mistral

mistral.api-key=<your-mistral-api-key>
mistral.model=ministral-8b-2512
mistral.base-url=https://api.mistral.ai/v1/chat/completions

llama.base-url=http://127.0.0.1:8080/completion
llama.model=gemma4:e2b

discord.token=<your-discord-bot-token>
memory.db-path=watney4.db
```

**This file is gitignored.** Don't commit secrets.

### 2. Discord Developer Portal
- Create an application at https://discord.com/developers/applications
- Enable MESSAGE CONTENT INTENT, SERVER MEMBERS INTENT, and VOICE STATE INTENT
- Copy the bot token into application.properties
- Use OAuth2 URL Generator with `bot` scope and permissions: Send Messages, Read Message History, Connect, Speak
- Invite the bot to a server you share

### 3. Build
```bash
./gradlew build
```

### 4. Run
```bash
./gradlew run
```

Or via the distribution:
```bash
./gradlew installDist
./build/install/Watney4/bin/Watney4
```

### 5. (Optional) Native image
```bash
./gradlew nativeCompile
./build/native/nativeCompile/Watney4
```

## Project Structure

```
src/main/kotlin/
├── core/
│   ├── Main.kt           # Entry point, wiring everything together
│   ├── Agent.kt           # The main loop — reads messages, calls LLM, runs tools
│   └── Watney4.kt         # System prompt / personality
├── interfaces/
│   ├── DiscordBot.kt      # JDA bot, slash commands, attachments, voice TTS
│   └── Cli.kt             # stdin reader
├── tools/
│   ├── Tool.kt            # Tool interface, ToolCall, ToolRegistry
│   ├── BashTool.kt
│   ├── ContextInjectTool.kt
│   ├── ContextStatusTool.kt
│   ├── ContextTruncateTool.kt
│   ├── CronTool.kt
│   ├── GlobTool.kt
│   ├── GrepTool.kt
│   ├── MemorySearchTool.kt
│   ├── OpencodeTool.kt
│   ├── ReadTool.kt
│   ├── SaveMemoryTool.kt
│   ├── ForgetMemoryTool.kt
│   ├── SystemStatusTool.kt
│   ├── WebFetchTool.kt
│   └── WebSearchTool.kt
└── utils/
    ├── ChatInterface.kt     # What CLI/Discord/Voice have in common
    ├── IncomingMessage.kt   # Message + who to reply to
    ├── LLMProvider.kt       # ChatMessage, LLMResult, interface
    ├── MistralProvider.kt   # Mistral API integration
    ├── LlamaCppProvider.kt  # Local llama.cpp
    ├── Context.kt           # Context window manager
    ├── MemoryStore.kt       # SQLite messages + memories + cron_jobs
    ├── CronScheduler.kt     # Background cron checker
    ├── TtsGenerator.kt      # Piper → ogg
    ├── VoiceChatManager.kt  # Voice join/leave, STT, send TTS to channel
    ├── Config.kt            # AppConfig from application.properties
    └── Logger.kt            # Structured logging
```

## How the Agent Loop Works

1. Read next message from the channel
2. Handle meta-commands (/clear, /exit, /quit)
3. Add user message to context and save to SQLite
4. Query the LLM with full context and tool definitions
5. If the response has tool calls, execute each one, append results to context, loop
6. If the response is plain text, send it to the user
7. Max 10 tool iterations per turn (keeps it from spiraling)

## Voice Chat Flow

1. You join a voice channel, DM the bot `/join`
2. Bot joins, starts listening
3. You speak
4. JDA delivers 48kHz stereo PCM packets every 20ms
5. VoiceChatManager downsamples to 16kHz mono, detects silence
6. After 1.2s of silence, accumulated audio gets written to a WAV temp file
7. Vosk (Python) transcribes it
8. Transcription goes to the agent as a text message
9. Response comes back as a DM (text) + voice channel (TTS)

## Configuration

| Property | Default | What it does |
|----------|---------|-------------|
| `log.level` | `info` | trace, debug, info, warn, error |
| `app.provider` | `mistral` | LLM backend |
| `mistral.api-key` | *(required)* | Mistral AI API key |
| `mistral.model` | `ministral-8b-2512` | Model ID |
| `mistral.base-url` | `https://api.mistral.ai/v1/chat/completions` | API endpoint |
| `llama.base-url` | `http://127.0.0.1:8080/completion` | Local endpoint |
| `llama.model` | `gemma4:e2b` | Local model |
| `discord.token` | *(required)* | Discord bot token |
| `memory.db-path` | `watney4.db` | SQLite path |

## Logging

```
HH:mm:ss.SSS LEVEL  [Name] Message
```

TRACE for tool loop internals, DEBUG for input/output, INFO for turns and lifecycle, WARN and ERROR when things break.

## Tests

```bash
./gradlew test
```

121 tests covering tools, memory, context management, and core agent logic.

## License

MIT
