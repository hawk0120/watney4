package utils

import kotlinx.coroutines.channels.SendChannel

interface ChatInterface {
    val label: String
    suspend fun start(sink: SendChannel<IncomingMessage>)
    suspend fun stop()
    suspend fun sendMessage(text: String)
}
