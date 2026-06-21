# Memory System

Three-tier memory architecture: Working Memory → Long-Term Memory → Archive.

## Tiers

### Working Memory (Context Window)

The agent's current session — a mutable `List<ChatMessage>` managed by `utils/Context.kt`. The last 50 messages are persisted in the `messages` table and reloaded on restart.

The agent can self-manage its context via tools: `context_truncate`, `context_inject`, `context_status`.

### Long-Term Memory (`ltm_entries` + `ltm_summaries` tables)

Persists session summaries for the **current week only** (Monday start). Each day's consolidation saves a summary to `ltm_entries`. Each entry can have multiple perspective summaries in `ltm_summaries` (factual, preferences, projects, goals, personal).

Loaded into the context as a system message on startup so the agent has week-level awareness.

### Archive (`ltm_archive` table)

Previous weeks' LTM entries, moved here during weekly rotation. Each archived entry stores:
- **Content** — a perspective-specific summary
- **Embedding** — BLOB of `FloatArray` (provider-generated, e.g. `mistral-embed` -> 1024 dimensions)
- **Semantic tags** — LLM-generated key phrases for hybrid search
- **Metadata** — flexible JSON blob

Semantic search across the archive uses cosine similarity on embeddings.

## Database Schema (SQLite)

```sql
-- Current week's long-term memory entries
CREATE TABLE ltm_entries (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    week_start TEXT NOT NULL,           -- ISO Monday YYYY-MM-DD
    content TEXT NOT NULL,
    metadata TEXT NOT NULL DEFAULT '{}',
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

-- Multi-perspective summaries per LTM entry
CREATE TABLE ltm_summaries (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    entry_id INTEGER NOT NULL,
    perspective TEXT NOT NULL,          -- factual | preferences | projects | goals | personal
    content TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (entry_id) REFERENCES ltm_entries(id)
);

-- Archived previous-week entries with embeddings
CREATE TABLE ltm_archive (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    original_entry_id INTEGER,
    week_start TEXT NOT NULL,
    content TEXT NOT NULL,
    perspective TEXT,
    semantic_tags TEXT,                 -- LLM-generated search key phrases
    embedding BLOB,                     -- FloatArray serialized to bytes
    metadata TEXT NOT NULL DEFAULT '{}',
    archived_at TEXT NOT NULL DEFAULT (datetime('now'))
);
```

## Key Files

| File | Role |
|------|------|
| `utils/MemoryStore.kt` | SQLite CRUD for all tables (messages, ltm_entries, ltm_summaries, ltm_archive, interaction_log) |
| `utils/LTMemoryManager.kt` | Orchestration: weekly rotation, multi-perspective summarization, semantic search, context loading |
| `utils/LLMProvider.kt` | Interface with `embed()` method for generating embeddings |
| `tools/SemanticSearchTool.kt` | Agent tool: `semantic_search(query, limit)` via cosine similarity |
| `tools/MemorySearchTool.kt` | Agent tool: `memory_search(query)` via SQL LIKE on messages |

## Weekly Rotation (`LTMemoryManager.rotateWeek()`)

Runs at the daily consolidation hour when a week rollover is detected:

1. Find the most recent week in `ltm_entries` that isn't the current week
2. For each entry, generate 5 perspective summaries (LLM calls with retry)
3. For each perspective, generate an embedding and save to `ltm_archive`
4. Delete the old week's entries
5. Generate a week highlight summary (one more LLM call)
6. Seed the new week's LTM with the highlight

## Perspective Generation

5 fixed perspectives, each with a dedicated LLM system prompt:

| Perspective | Focus |
|-------------|-------|
| factual | Dates, numbers, decisions, concrete info |
| preferences | User likes, dislikes, tastes |
| projects | Status, plans, technical decisions, blockers |
| goals | Milestones, aspirations, long-term plans |
| personal | Relationships, routines, lifestyle |

Each call includes retry logic (up to 3 attempts) if the output is empty, too short (<20 chars), or contains refusal phrases.

## Semantic Search (`LTMemoryManager.semanticSearch()`)

1. Embed the query via `LLMProvider.embed()`
2. Load all archive entries with non-null embeddings
3. Compute cosine similarity against each
4. Sort descending, return top-K formatted results

```kotlin
fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    var dotProduct = 0f
    var normA = 0f
    var normB = 0f
    for (i in a.indices) {
        dotProduct += a[i] * b[i]
        normA += a[i] * a[i]
        normB += b[i] * b[i]
    }
    val denom = sqrt(normA) * sqrt(normB)
    return if (denom == 0f) 0f else dotProduct / denom
}
```

## Embedding Provider

Configurable independently from the chat provider via `application.properties`:

```properties
embedding.provider=mistral       # mistral | llamacpp | openrouter
embedding.model=mistral-embed    # model-specific default
```

Defaults to Mistral (`mistral-embed`, 1024 dimensions) regardless of the chat provider. Each provider implements `LLMProvider.embed()` via its respective embeddings API.

## Removed

The old `memories` key/value table and its associated tools (`save_memory`, `forget_memory`) have been removed. The `memory_search` tool still works — it searches the `messages` table by SQL `LIKE` for current-chat keyword lookup.
