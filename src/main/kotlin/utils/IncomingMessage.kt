package utils

data class IncomingMessage(
    val text: String,
    val replyTo: ChatInterface,
    val type: String = "user"
)
