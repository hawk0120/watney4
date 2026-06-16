package utils

import com.google.gson.Gson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.ToolCall

class OpenRouterProviderTest {

    private val gson = Gson()

    @Test
    fun `OpenRouterMessage with all fields`() {
        val calls = listOf(OpenRouterToolCall("1", "function", OpenRouterFunctionCall("read", "{\"path\":\"/tmp/test\"}")))
        val msg = OpenRouterMessage(
            role = "assistant",
            content = "Let me check",
            tool_calls = calls,
            tool_call_id = "call_1",
            name = "read_file"
        )
        assertEquals("assistant", msg.role)
        assertEquals("Let me check", msg.content)
        assertEquals(calls, msg.tool_calls)
        assertEquals("call_1", msg.tool_call_id)
        assertEquals("read_file", msg.name)
    }

    @Test
    fun `OpenRouterMessage with null content`() {
        val msg = OpenRouterMessage(role = "user", content = null)
        assertNull(msg.content)
    }

    @Test
    fun `OpenRouterMessage default values`() {
        val msg = OpenRouterMessage(role = "user", content = "hi")
        assertNull(msg.tool_calls)
        assertNull(msg.tool_call_id)
        assertNull(msg.name)
    }

    @Test
    fun `OpenRouterRequest serialization`() {
        val messages = listOf(
            OpenRouterMessage(role = "user", content = "Hello"),
            OpenRouterMessage(role = "assistant", content = "Hi there")
        )
        val request = OpenRouterRequest(
            model = "google/gemma-4-26b-a4b-it:free",
            messages = messages,
            tools = null,
            stream = false
        )
        val json = gson.toJson(request)
        assertTrue(json.contains("google/gemma-4-26b-a4b-it:free"))
        assertTrue(json.contains("Hello"))
        assertTrue(json.contains("Hi there"))
        assertTrue(json.contains("\"stream\":false"))
    }

    @Test
    fun `OpenRouterRequest with tools`() {
        val messages = listOf(OpenRouterMessage(role = "user", content = "List files"))
        val tools = listOf(
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to "read",
                    "description" to "Read a file",
                    "parameters" to mapOf("type" to "object", "properties" to mapOf<String, Any>())
                )
            )
        )
        val request = OpenRouterRequest(
            model = "google/gemma-4-26b-a4b-it:free",
            messages = messages,
            tools = tools,
            stream = false
        )
        val json = gson.toJson(request)
        assertTrue(json.contains("tools"))
        assertTrue(json.contains("read"))
    }

    @Test
    fun `OpenRouterResponse deserialization with tool calls`() {
        val rawJson = """
        {
            "id": "gen-abc123",
            "choices": [
                {
                    "index": 0,
                    "finish_reason": "tool_calls",
                    "message": {
                        "role": "assistant",
                        "content": null,
                        "tool_calls": [
                            {
                                "id": "call_1",
                                "type": "function",
                                "function": {
                                    "name": "read",
                                    "arguments": "{\"filePath\": \"/tmp/test.txt\"}"
                                }
                            }
                        ]
                    }
                }
            ],
            "usage": {
                "prompt_tokens": 100,
                "completion_tokens": 20,
                "total_tokens": 120
            }
        }
        """.trimIndent()

        val response = gson.fromJson(rawJson, OpenRouterResponse::class.java)
        assertEquals("gen-abc123", response.id)
        assertEquals(1, response.choices.size)
        assertEquals("tool_calls", response.choices[0].finish_reason)
        assertNull(response.choices[0].message.content)
        assertEquals(1, response.choices[0].message.tool_calls?.size)
        assertEquals("call_1", response.choices[0].message.tool_calls!![0].id)
        assertEquals("read", response.choices[0].message.tool_calls!![0].function.name)
        assertEquals(100, response.usage?.prompt_tokens)
        assertEquals(20, response.usage?.completion_tokens)
        assertEquals(120, response.usage?.total_tokens)
    }

    @Test
    fun `OpenRouterResponse deserialization plain text`() {
        val rawJson = """
        {
            "id": "gen-xyz789",
            "choices": [
                {
                    "index": 0,
                    "finish_reason": "stop",
                    "message": {
                        "role": "assistant",
                        "content": "Sure, here's the answer."
                    }
                }
            ],
            "usage": {
                "prompt_tokens": 50,
                "completion_tokens": 10,
                "total_tokens": 60
            }
        }
        """.trimIndent()

        val response = gson.fromJson(rawJson, OpenRouterResponse::class.java)
        assertEquals("gen-xyz789", response.id)
        assertEquals("stop", response.choices[0].finish_reason)
        assertEquals("Sure, here's the answer.", response.choices[0].message.content)
        assertNull(response.choices[0].message.tool_calls)
        assertEquals(50, response.usage?.prompt_tokens)
    }

    @Test
    fun `OpenRouterErrorResponse deserialization`() {
        val rawJson = """
        {
            "error": {
                "message": "Insufficient credits",
                "type": "insufficient_quota",
                "code": 429
            }
        }
        """.trimIndent()

        val error = gson.fromJson(rawJson, OpenRouterErrorResponse::class.java)
        assertEquals("Insufficient credits", error.error?.message)
        assertEquals("insufficient_quota", error.error?.type)
        assertEquals(429, error.error?.code)
    }

    @Test
    fun `OpenRouterProvider model name format`() {
        val provider = OpenRouterProvider(apiKey = "test-key")
        assertEquals("openrouter:google/gemma-4-26b-a4b-it:free", provider.modelName)
    }

    @Test
    fun `OpenRouterProvider custom model name`() {
        val provider = OpenRouterProvider(
            apiKey = "test-key",
            model = "anthropic/claude-3.5-sonnet"
        )
        assertEquals("openrouter:anthropic/claude-3.5-sonnet", provider.modelName)
    }

    @Test
    fun `Config createProvider returns OpenRouterProvider`() {
        val config = AppConfig(
            provider = "openrouter",
            logLevel = LogLevel.INFO,
            mistralApiKey = "mk",
            mistralModel = "mm",
            mistralBaseUrl = "mb",
            llamaBaseUrl = "lb",
            llamaModel = "lm",
            discordToken = "dt",
            memoryDbPath = ":memory:",
            consolidationTimezone = "UTC",
            consolidationHour = 3,
            openrouterApiKey = "or-key",
            openrouterModel = "google/gemma-4-26b-a4b-it:free",
            openrouterBaseUrl = "https://openrouter.ai/api/v1/chat/completions"
        )
        val provider = AppConfig.createProvider(config)
        assertTrue(provider is OpenRouterProvider)
        assertEquals("openrouter:google/gemma-4-26b-a4b-it:free", provider.modelName)
    }

    @Test
    fun `OpenRouterToolCall default type is function`() {
        val call = OpenRouterToolCall(
            id = "call_1",
            function = OpenRouterFunctionCall("read", "{}")
        )
        assertEquals("function", call.type)
    }
}
