# Watney4

Yet another AI agent that runs via CLI and Discord DM, powered by Mistral AI with function-calling tools, SQLite memory, voice chat, and local TTS.

## Architecture

```
┌─────────┐   ┌────────────┐   ┌───────────┐   ┌──────────────┐
│   CLI   │   │  Discord   │   │  Voice    │   │   Cron       │
│ (stdin) │──►│ Bot (JDA)  │──►│ Chat     │──►│   Scheduler  │──►
└─────────┘   └────────────┘   │ Manager  │   └──────────────┘
                               └──────────┘
                                   │
                                   ▼
                           ┌──────────────┐
                           │  Channel<     │
                           │  IncomingMsg> │
                           └──────┬───────┘
                                   │
                                   ▼
                           ┌──────────────┐
                           │    Agent      │──► LLM (Mistral / llama.cpp)
                           │  (tool loop)  │──► Tools (11x)
                           └──────┬───────┘
                                   │
                                   ▼
                           ┌──────────────┐
                           │  MemoryStore │ (SQLite)
                           └──────────────┘
```

Two input interfaces (CLI + Discord) push `IncomingMessage(text, replyTo)` into a shared coroutine `Channel`. The agent reads from the channel, queries the LLM, executes tool calls in a loop, and sends responses back via the `replyTo` interface.

## Features

### Interfaces
- **CLI** — stdin/stdout, prompts for input
- **Discord Bot** — JDA 5.x, DMs only, slash commands
- **Voice Chat** — join a voice channel via `/join`, speak, bot transcribes and responds with TTS in-channel + text in DM

### Slash Commands
| Command | Description |
|---------|-------------|
| `/clear` | Reset conversation context |
| `/voice` | Toggle file-based TTS (.ogg in DM) |
| `/status` | Uptime, message count, heap, JVM version |
| `/join` | Join your current Discord voice channel |
| `/leave` | Leave the voice channel |

### LLM Providers
- **Mistral AI** — cloud API, supports function calling (all 11 tools)
- **llama.cpp** — local, no tool support

### Tools (11 function-calling tools)
| Tool | Description |
|------|-------------|
| `read` | Read any file by absolute path |
| `write` | Write content to any file (creates parent dirs) |
| `bash` | Run shell commands (30s timeout, destructive commands blocked) |
| `glob` | Recursive file search by name pattern |
| `grep` | Regex content search across files |
| `web_search` | DuckDuckGo search via Python |
| `web_fetch` | HTTP GET a URL (text content only) |
| `cron` | Schedule/remove/list recurring tasks (SQLite-backed) |
| `memory_search` | Search past conversation history by keyword |
| `opencode` | Delegate complex multi-step coding tasks to Opencode |

### Persistence
- **SQLite** — conversation history (last 50 messages loaded on startup) + cron jobs
- **Memory search** — keyword search across all stored messages

### Voice (New)
- **Speech-to-text** — Vosk (offline, small English model, ~40MB)
- **Text-to-speech** — espeak-ng + mbrola-us1 voice, played directly into Discord voice channel
- Commands: `/join` (detects your voice channel across shared guilds), `/leave`

### TTS (File-based)
- `espeak-ng` → WAV → `ffmpeg` → `.ogg`, sent as Discord file attachment
- Toggled via `/voice` slash command
- Max ~30 seconds of speech

## Requirements

- Java 21 (GraalVM or OpenJDK)
- Gradle (wrapper included)
- Python 3.11+
- Linux (Ubuntu/Debian-based)

### Python packages
```bash
pip3 install vosk ddgs --break-system-packages
```

### System packages
```bash
sudo apt install espeak-ng mbrola-us1 ffmpeg opus-tools
```

Vosk downloads its model automatically on first use (~40MB).

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

**This file is gitignored.** Never commit secrets.

### 2. Discord Developer Portal
- Create an application at https://discord.com/developers/applications
- Enable **MESSAGE CONTENT INTENT** and **SERVER MEMBERS INTENT** and **VOICE STATE INTENT** under Bot > Privileged Gateway Intents
- Copy the bot token into `application.properties`
- Use OAuth2 URL Generator with `bot` scope and `Send Messages`, `Read Message History`, `Connect`, `Speak` permissions
- Invite the bot to a server you're in

### 3. Build
```bash
./gradlew build
```

### 4. Run
```bash
./gradlew run
```

Or use the distribution:
```bash
./gradlew installDist
./build/install/Watney4/bin/Watney4
```

## Project Structure

```
src/main/kotlin/
├── core/
│   ├── Main.kt          # Entry point, wiring
│   ├── Agent.kt          # Async agent loop with tool execution
│   └── Watney4.kt        # System prompt / persona
├── interfaces/
│   ├── DiscordBot.kt     # JDA bot, slash commands, attachments, voice TTS
│   └── Cli.kt            # stdin reader
├── tools/
│   ├── Tool.kt           # Tool interface, ToolCall, ToolRegistry
│   ├── BashTool.kt       # Shell commands
│   ├── CronTool.kt       # Recurring task scheduling
│   ├── GlobTool.kt       # File search by pattern
│   ├── GrepTool.kt       # Content search by regex
│   ├── MemorySearchTool.kt  # Conversation history search
│   ├── OpencodeTool.kt   # Multi-step coding delegation
│   ├── ReadTool.kt       # File reader
│   ├── WriteTool.kt      # File writer
│   ├── WebFetchTool.kt   # URL fetcher
│   └── WebSearchTool.kt  # DuckDuckGo search
└── utils/
    ├── ChatInterface.kt    # Interface for CLI/Discord/Voice
    ├── IncomingMessage.kt  # Message + reply target
    ├── LLMProvider.kt      # ChatMessage, LLMResult, LLMProvider interface
    ├── MistralProvider.kt  # Mistral AI API
    ├── LlamaCppProvider.kt # Local llama.cpp
    ├── MemoryStore.kt      # SQLite messages + cron_jobs
    ├── CronScheduler.kt    # Background cron checker
    ├── TtsGenerator.kt     # espeak-ng → ogg file
    ├── VoiceChatManager.kt # Voice join/leave, STT, send TTS to channel
    ├── Config.kt           # AppConfig from application.properties
    └── Logger.kt           # Structured logging (TRACE/DEBUG/INFO/WARN/ERROR)
```

## How the Agent Loop Works

1. Read next message from `Channel<IncomingMessage>`
2. Handle meta-commands (`/clear`, `/exit`, `/quit`)
3. Add user message to context + persist to SQLite
4. Query LLM with full context + tool definitions
5. If response has `tool_calls`, execute each tool, append results to context, loop
6. If response is plain text, send to user, mark turn complete
7. Max 10 tool iterations per turn

## Voice Chat Flow

1. User joins a Discord voice channel, DMs bot `/join`
2. Bot joins the same channel, starts receiving audio
3. User speaks
4. JDA delivers 48kHz stereo PCM packets every 20ms
5. VoiceChatManager downsamples to 16kHz mono, detects silence
6. On 1.2s of silence, accumulated PCM is written to a WAV temp file
7. Vosk (Python) transcribes the audio
8. Transcription sent to agent as a text message
9. Agent response goes to DM (text) + voice channel (TTS via espeak-ng)

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `log.level` | `info` | One of: `trace`, `debug`, `info`, `warn`, `error` |
| `app.provider` | `mistral` | LLM backend: `mistral` or `llamacpp` |
| `mistral.api-key` | *(required)* | Mistral AI API key |
| `mistral.model` | `ministral-8b-2512` | Mistral model ID |
| `mistral.base-url` | `https://api.mistral.ai/v1/chat/completions` | API endpoint |
| `llama.base-url` | `http://127.0.0.1:8080/completion` | Local llama.cpp endpoint |
| `llama.model` | `gemma4:e2b` | llama.cpp model |
| `discord.token` | *(required)* | Discord bot token |
| `memory.db-path` | `watney4.db` | SQLite database path |

## Logging

```
HH:mm:ss.SSS LEVEL  [Name] Message
```

Levels: `TRACE` (tool loop details), `DEBUG` (input/output), `INFO` (turns, errors, lifecycle), `WARN`, `ERROR`.

## License

MIT
