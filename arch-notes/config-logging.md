# Configuration & Logging

## Config (`utils/Config.kt`)

`AppConfig` loads `application.properties` from the classpath:

| Property | Default | Description |
|---|---|---|
| `app.provider` | `"mistral"` | LLM provider: `mistral` or `llamacpp` |
| `log.level` | `"info"` | Log level: trace/debug/info/warn/error |
| `mistral.api-key` | *(required)* | Mistral AI API key |
| `mistral.model` | `"ministral-8b-2512"` | Mistral model name |
| `mistral.base-url` | Mistral API URL | Custom endpoint for Mistral |
| `llama.base-url` | `http://127.0.0.1:8080/completion` | llama.cpp endpoint |
| `llama.model` | `"gemma4:e2b"` | Model label (informational) |
| `discord.token` | *(required)* | Discord bot token |
| `memory.db-path` | `"watney4.db"` | SQLite database path |

The `createProvider()` factory method selects and instantiates the correct `LLMProvider` based on `app.provider`.

## Logging (`utils/Logger.kt`)

Custom structured logging (no external dependency):

- **LogLevel** enum: TRACE < DEBUG < INFO < WARN < ERROR with priority comparison
- **Logger.getLogger(name, level)** creates a named logger that respects the configured level
- Output format: `[timestamp] [name] [level] message`
- All components accept a `logLevel` parameter for consistent control
