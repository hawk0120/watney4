package utils

import kotlinx.coroutines.channels.SendChannel

class SelfChatInterface : ChatInterface {
    override val label = "self"
    override suspend fun start(sink: SendChannel<IncomingMessage>) {}
    override suspend fun stop() {}
    override suspend fun sendMessage(text: String) {}
}
