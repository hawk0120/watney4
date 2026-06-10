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
    val matrixHomeserver: String = "",
    val matrixUsername: String = "",
    val matrixPassword: String = ""
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
                matrixHomeserver = props.getProperty("matrix.homeserver", ""),
                matrixUsername = props.getProperty("matrix.username", ""),
                matrixPassword = props.getProperty("matrix.password", "")
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
            else -> error("Unknown provider: ${config.provider}. Use 'mistral' or 'llamacpp'.")
        }
    }
}
