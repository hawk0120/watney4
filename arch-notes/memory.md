# Memory Store

SQLite-backed persistent storage for chat history and key/value facts.

## Store (`utils/MemoryStore.kt`)

Uses `sqlite-jdbc`. Two tables in `watney4.db`:

**messages** — Chat history:
```sql
CREATE TABLE messages (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    role TEXT NOT NULL,
    content TEXT NOT NULL,
    timestamp TEXT NOT NULL DEFAULT (datetime('now'))
)
```

**memories** — Key/value facts:
```sql
CREATE TABLE memories (
    key TEXT PRIMARY KEY,
    content TEXT NOT NULL,
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
)
```

## Startup

On agent start, `MemoryStore` loads:
- Last 50 messages (to restore conversational context across restarts)
- All saved memories (injected as a system message)

## Tools

| Tool | Action |
|------|--------|
| **MemorySearchTool** | `SELECT content FROM messages WHERE content LIKE '%query%' ORDER BY id DESC LIMIT 10` |
| **SaveMemoryTool** | `INSERT OR REPLACE INTO memories (key, content, updated_at) VALUES (?, ?, datetime('now'))` |
| **ForgetMemoryTool** | `DELETE FROM memories WHERE key = ?` |
