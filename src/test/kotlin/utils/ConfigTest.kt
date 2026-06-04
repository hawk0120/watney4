package utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConfigTest {

    @Test
    fun `LogLevel priority values for config`() {
        assertEquals(1, LogLevel.DEBUG.priority)
        assertEquals(2, LogLevel.INFO.priority)
        assertEquals(3, LogLevel.WARN.priority)
        assertEquals(4, LogLevel.ERROR.priority)
    }

    @Test
    fun `AppConfig data class stores fields correctly`() {
        val config = AppConfig(
            provider = "mistral",
            logLevel = LogLevel.DEBUG,
            mistralApiKey = "test-key",
            mistralModel = "test-model",
            mistralBaseUrl = "https://test.url",
            llamaBaseUrl = "http://localhost:8080",
            llamaModel = "test-llama",
            discordToken = "discord-test",
            memoryDbPath = ":memory:"
        )
        assertEquals("mistral", config.provider)
        assertEquals(LogLevel.DEBUG, config.logLevel)
        assertEquals("test-key", config.mistralApiKey)
        assertEquals("test-model", config.mistralModel)
        assertEquals("https://test.url", config.mistralBaseUrl)
        assertEquals("http://localhost:8080", config.llamaBaseUrl)
        assertEquals("test-llama", config.llamaModel)
        assertEquals("discord-test", config.discordToken)
        assertEquals(":memory:", config.memoryDbPath)
    }

    @Test
    fun `AppConfig default values`() {
        val config = AppConfig(
            provider = "mistral",
            logLevel = LogLevel.INFO,
            mistralApiKey = "key",
            mistralModel = "ministral-8b-2512",
            mistralBaseUrl = "https://api.mistral.ai/v1/chat/completions",
            llamaBaseUrl = "http://127.0.0.1:8080/completion",
            llamaModel = "gemma4:e2b",
            discordToken = "token",
            memoryDbPath = "watney4.db"
        )
        assertEquals("ministral-8b-2512", config.mistralModel)
        assertEquals("https://api.mistral.ai/v1/chat/completions", config.mistralBaseUrl)
        assertEquals("http://127.0.0.1:8080/completion", config.llamaBaseUrl)
        assertEquals("gemma4:e2b", config.llamaModel)
        assertEquals("watney4.db", config.memoryDbPath)
    }
}
