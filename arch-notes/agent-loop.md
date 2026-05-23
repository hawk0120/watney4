# Agent Loop

The core of Watney4. An infinite coroutine that reads `IncomingMessage` objects from a shared `Channel` and processes them through an LLM with optional tool execution.

## Architecture

```
CLI ─┐
Discord ─┼─→ inbox Channel ──→ Agent loop ──→ LLM query ──→ tool execution ──→ reply
Voice ─┘                            ↑                        │
Cron ─┐                             └── context ←────────────┘
Reminder ─┘
```

`Main.kt` starts all interface coroutines (CLI, Discord), each writing to the same `inbox` channel. The `Agent` loop reads from it one message at a time.

## Flow

1. Receive an `IncomingMessage` from the inbox
2. Handle meta-commands (`/exit`, `/clear`) directly
3. Prepend `[type]` prefix for non-user messages (cron, reminder)
4. Append to context message list, save to memory
5. Query the LLM with the full context + tool definitions
6. If the LLM returns tool calls, execute each tool and append results to context
7. Loop back to step 5 (up to `maxToolIterations = 15` times)
8. Send the final response via `msg.replyTo.sendMessage()`

## Key Files

- `core/Main.kt` — entry point, wires everything together
- `core/Agent.kt` — the main loop
- `core/Watney4.kt` — system prompt / persona
- `utils/IncomingMessage.kt` — data class for input
- `utils/ChatInterface.kt` — abstract I/O interface

## Progress Throttle

During tool execution, progress messages are buffered and flushed at most once every 2 seconds (`throttledProgress` in `Agent.kt:174`). This prevents flooding the user with intermediate messages during rapid tool calls.
