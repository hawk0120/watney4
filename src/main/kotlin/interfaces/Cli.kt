package interfaces

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import utils.ChatInterface
import utils.IncomingMessage
import utils.LogLevel
import utils.Logger

class Cli(
    private val logLevel: LogLevel = LogLevel.INFO
) : ChatInterface {
    override val label = "CLI"
    private val log = Logger.getLogger(label, logLevel)

    override suspend fun start(sink: SendChannel<IncomingMessage>) {
        log.info("CLI interface ready")
        withContext(Dispatchers.IO) {
            while (isActive) {
                print("user: ")
                val line = readLine() ?: break
                sink.send(IncomingMessage(line, this@Cli))
            }
        }
    }

    override suspend fun stop() {
        log.info("CLI interface stopped")
    }

    override suspend fun sendMessage(text: String) {
        println("Watney4: $text")
    }
}
