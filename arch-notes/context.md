# Context Management

The `Context` class wraps the agent's mutable message list and provides operations to manage its size and content.

## Context (`utils/Context.kt`)

Bound to the agent's message list at startup via `ctx.bind(messages)`. Methods:

- `truncate(keepLast)` — Wipes all non-system messages except the N most recent
- `inject(role, content, index)` — Inserts a synthetic message at position `index` (clamped to valid range)
- `clearNonSystem()` — Removes every message except the system prompt
- `size` / `messageCount` — Convenience accessors

## Tools

| Tool | Action |
|------|--------|
| **ContextTruncateTool** | Calls `truncate()` with the requested `keepLast` count |
| **ContextInjectTool** | Calls `inject()` with the specified role, content, and position |
| **ContextStatusTool** | Reports message counts by role, total characters, and a rough token estimate (chars / 4) |

These let the agent self-manage its context window when it gets long, without waiting for the user to intervene.
