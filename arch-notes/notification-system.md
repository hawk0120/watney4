# Agent Notification System

The notification system lets the agent receive typed messages from itself, cron jobs, and external hooks — not just user input.

## Architecture

```
User (CLI/Discord)  ──→  IncomingMessage(type="user")
CronScheduler       ──→  IncomingMessage(type="cron")
ReminderScheduler   ──→  IncomingMessage(type="reminder")

                            ↓
                    HookRegistry.intercept()   ← hooks can drop or modify
                            ↓
                    Agent loop processes message,
                    sees "[cron]" / "[reminder]" / "[user]" in context
```

## Components

### `IncomingMessage` (`utils/IncomingMessage.kt`)
Added a `type: String = "user"` field. Default `"user"` means CLI and Discord need no changes.

### `SelfChatInterface` (`utils/SelfChatInterface.kt`)
A no-op `ChatInterface` with `label = "self"`. Used as the `replyTo` for cron and reminder messages so responses are processed silently (not broadcast to Discord).

### `ReminderScheduler` (`utils/ReminderScheduler.kt`)
One-shot delayed notification scheduler. The `schedule(prompt, delayMinutes)` method launches a coroutine that waits N minutes then sends an `IncomingMessage(type="reminder")` back to the inbox.

### `AgentHook` & `HookRegistry` (`utils/AgentHook.kt`)
```kotlin
interface AgentHook {
    suspend fun onNotification(msg: IncomingMessage): IncomingMessage? = msg
}
```
- Return the (possibly modified) message to continue processing
- Return `null` to silently consume the notification

`HookRegistry` chains multiple hooks in order. If any hook returns null, processing stops.

### `RemindTool` (`tools/RemindTool.kt`)
Tool exposed to the agent:
- `remind(prompt, delayMinutes)` — schedule a one-time self-notification

### `CronScheduler` (`utils/CronScheduler.kt`)
Now tags messages with `type = "cron"` and defaults `replyTo` to `SelfChatInterface`.

### `Agent` (`core/Agent.kt`)
- Accepts `HookRegistry` in constructor
- After `inbox.receiveCatching()`, calls `hooks.intercept(msg)` — if null, the message is skipped
- Non-user messages get a `[type]` prefix in context: `[cron] check status`, `[reminder] follow up`

## Notification types

| Type | Source | Context prefix |
|---|---|---|
| `user` | CLI, Discord | (none) |
| `cron` | CronScheduler | `[cron]` |
| `reminder` | ReminderScheduler | `[reminder]` |
| `system` | (future) | `[system]` |

## Adding a new notification source

```kotlin
inbox.send(IncomingMessage("your text", selfChatInterface, "your_type"))
```

Then implement an `AgentHook` if you need to intercept or transform it.
