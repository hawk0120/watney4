# LLM Providers

Abstracted behind the `LLMProvider` interface, allowing either a cloud API or local model.

## Interface (`utils/LLMProvider.kt`)

```kotlin
interface LLMProvider {
    suspend fun query(messages: List<ChatMessage>, tools: List<ToolDef>? = null): LLMResult
    suspend fun isAvailable(): Boolean
}
```

`LLMResult` is a sealed class: `Success(response, calls)` or `Error(message)`. `ToolCall` objects carry `id`, `name`, and `arguments` (a `Map<String, Any?>`).

## MistralProvider (`utils/MistralProvider.kt`)

- Sends a chat completion request to the Mistral AI API
- Serializes messages to JSON with tool definitions
- Parses tool calls from the response's `tool_calls` array
- Configurable base URL, model, and API key

## LlamaCppProvider (`utils/LlamaCppProvider.kt`)

- Sends to a local llama.cpp `/completion` endpoint
- Concatenates messages into a single prompt string (no tool-call support)
- Parses tool calls from the response text using regex

## Selection

`AppConfig.createProvider()` in `Config.kt` chooses based on `app.provider`:
- `"mistral"` → MistralProvider
- `"llamacpp"` → LlamaCppProvider
