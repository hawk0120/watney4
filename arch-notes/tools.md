# Tool System

Tools let the LLM interact with the filesystem, run commands, search the web, and manage itself.

## Architecture (`tools/Tool.kt`)

```kotlin
interface Tool {
    val name: String
    val description: String
    val parameters: JsonObject  // JSON Schema
    suspend fun execute(args: Map<String, Any?>, progress: (String) -> Unit): String
}
```

`ToolRegistry` holds a list of tools, generates OpenAI-style tool definitions for the LLM, and dispatches execution by name.

## Tools

| Tool | File | Purpose |
|------|------|---------|
| **ReadTool** | `tools/ReadTool.kt` | Read a file from disk |
| **WriteTool** | `tools/WriteTool.kt` | Write content to a file (creates parent dirs) |
| **BashTool** | `tools/BashTool.kt` | Run a shell command (30s default timeout, blocks dangerous commands) |
| **GlobTool** | `tools/GlobTool.kt` | Walk a directory tree matching a glob pattern |
| **GrepTool** | `tools/GrepTool.kt` | Regex search across file contents |
| **OpencodeTool** | `tools/OpencodeTool.kt` | Delegate complex multi-step tasks to `opencode run` |
| **WebFetchTool** | `tools/WebFetchTool.kt` | HTTP GET a URL, return body (truncated to 50k chars) |
| **WebSearchTool** | `tools/WebSearchTool.kt` | DuckDuckGo search via Python/ddgs |
| **MemorySearchTool** | `tools/MemorySearchTool.kt` | SQL `LIKE` search on chat history |
| **SaveMemoryTool** | `tools/SaveMemoryTool.kt` | Upsert a key/value fact |
| **ForgetMemoryTool** | `tools/ForgetMemoryTool.kt` | Delete a memory by key |
| **CronTool** | `tools/CronTool.kt` | Add/remove/list cron jobs |
| **RemindTool** | `tools/RemindTool.kt` | Schedule a one-shot reminder |
| **ContextTruncateTool** | `tools/ContextTruncateTool.kt` | Remove old messages from context |
| **ContextInjectTool** | `tools/ContextInjectTool.kt` | Insert a synthetic message into context |
| **ContextStatusTool** | `tools/ContextStatusTool.kt` | Show context size/ composition |
| **SystemStatusTool** | `tools/SystemStatusTool.kt` | Server health: CPU, RAM, disk, uptime, services |

## Tool Execution

The LLM returns tool calls as `{name, arguments}` objects. The agent iterates through each call, executes it via `ToolRegistry`, and appends the result as a `tool`-role message to context for the LLM's next turn.

Progress callbacks stream intermediate text back to the user (throttled to 2s intervals).
