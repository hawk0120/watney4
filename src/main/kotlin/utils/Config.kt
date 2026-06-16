package utils

import java.io.InputStreamReader
import java.util.Properties

data class AppConfig(
    val provider: String,
    val logLevel: LogLevel,
    val mistralApiKey: String,
    val mistralModel: String,
    val mistralBaseUrl: String,
    val llamaBaseUrl: String,
    val llamaModel: String,
    val discordToken: String,
    val memoryDbPath: String,
    val consolidationTimezone: String,
    val consolidationHour: Int,
    val openrouterApiKey: String,
    val openrouterModel: String,
    val openrouterBaseUrl: String
) {
    companion object {
        fun load(): AppConfig {
            val props = Properties()
            val stream = Thread.currentThread().contextClassLoader
                .getResourceAsStream("application.properties")
                ?: error("application.properties not found on classpath")
            props.load(InputStreamReader(stream))
            return AppConfig(
                provider = props.getProperty("app.provider", "mistral"),
                logLevel = parseLogLevel(props.getProperty("log.level", "info")),
                mistralApiKey = props.getProperty("mistral.api-key")
                    ?: error("mistral.api-key is required in application.properties"),
                mistralModel = props.getProperty("mistral.model", "ministral-8b-2512"),
                mistralBaseUrl = props.getProperty("mistral.base-url", "https://api.mistral.ai/v1/chat/completions"),
                llamaBaseUrl = props.getProperty("llama.base-url", "http://127.0.0.1:8080/completion"),
                llamaModel = props.getProperty("llama.model", "gemma4:e2b"),
                discordToken = props.getProperty("discord.token")
                    ?: error("discord.token is required in application.properties"),
                memoryDbPath = props.getProperty("memory.db-path", "watney4.db"),
                consolidationTimezone = props.getProperty("consolidation.timezone", "Europe/Berlin"),
                consolidationHour = props.getProperty("consolidation.hour", "3").toInt(),
                openrouterApiKey = props.getProperty("openrouter.api-key", ""),
                openrouterModel = props.getProperty("openrouter.model", "google/gemma-4-26b-a4b-it:free"),
                openrouterBaseUrl = props.getProperty("openrouter.base-url", "https://openrouter.ai/api/v1/chat/completions")
            )
        }

        private fun parseLogLevel(value: String): LogLevel = when (value.lowercase()) {
            "debug" -> LogLevel.DEBUG
            "info" -> LogLevel.INFO
            "warn" -> LogLevel.WARN
            "error" -> LogLevel.ERROR
            else -> LogLevel.INFO
        }

        fun createProvider(config: AppConfig): LLMProvider = when (config.provider) {
            "mistral" -> MistralProvider(
                apiKey = config.mistralApiKey,
                model = config.mistralModel,
                baseUrl = config.mistralBaseUrl,
                logLevel = config.logLevel
            )
            "llamacpp" -> LlamaCppProvider(
                baseUrl = config.llamaBaseUrl,
                model = config.llamaModel,
                logLevel = config.logLevel
            )
            "openrouter" -> OpenRouterProvider(
                apiKey = config.openrouterApiKey,
                model = config.openrouterModel,
                baseUrl = config.openrouterBaseUrl,
                logLevel = config.logLevel
            )
            else -> error("Unknown provider: ${config.provider}. Use 'mistral', 'llamacpp', or 'openrouter'.")
        }
    }
}
