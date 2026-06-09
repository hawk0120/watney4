package utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ReminderScheduler(
    private val inbox: kotlinx.coroutines.channels.SendChannel<IncomingMessage>,
    private val replyTo: ChatInterface = SelfChatInterface(),
    private val scope: CoroutineScope
) {
    suspend fun schedule(prompt: String, delayMinutes: Int): String {
        val delayMs = (delayMinutes.toLong() * 60_000L).coerceAtLeast(1000L)
        scope.launch {
            delay(delayMs)
            inbox.send(IncomingMessage(prompt, replyTo, "reminder"))
        }
        return "Reminder scheduled for $delayMinutes minute(s): \"$prompt\""
    }
}
