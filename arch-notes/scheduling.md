# Scheduling: Cron & Reminders

Two background scheduling systems that inject messages into the agent loop.

## Cron Scheduler (`utils/CronScheduler.kt`)

SQLite-backed recurring task scheduler. Cron jobs are stored in a `cron_jobs` table with `id`, `schedule`, `prompt`, and `active` fields. A coroutine polls every 30 seconds for due jobs. When a job fires, it sends an `IncomingMessage(type="cron")` to the inbox.

The `CronTool` exposes add/remove/list actions to the agent.

## Reminder Scheduler (`utils/ReminderScheduler.kt`)

One-shot delayed notifications. `schedule(prompt, delayMinutes)` launches a coroutine that waits N minutes then sends an `IncomingMessage(type="reminder")`.

`RemindTool` exposes the schedule action.

## Internal Messaging (`utils/SelfChatInterface.kt`)

Both schedulers use `SelfChatInterface` as the `replyTo` for their messages. `SelfChatInterface.sendMessage()` is a no-op — internal messages don't echo to Discord or CLI.

The agent prepends `[cron]` or `[reminder]` to these messages when adding them to context.

## Flow

```
CronScheduler ──→ IncomingMessage(type="cron", replyTo=selfChat)
ReminderScheduler ──→ IncomingMessage(type="reminder", replyTo=selfChat)
                              ↓
                         Agent loop
                              ↓
                    [cron] check status added to context
                              ↓
                    LLM processes, response → selfChat.sendMessage() = no-op
```
