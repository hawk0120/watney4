package interfaces

import de.connect2x.lognity.api.backend.Backend
import de.connect2x.lognity.backend.DefaultBackend
import de.connect2x.trixnity.clientserverapi.client.MatrixClientAuthProviderData
import de.connect2x.trixnity.clientserverapi.client.MatrixClientAuthProviderDataStore
import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClient
import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClientImpl
import de.connect2x.trixnity.clientserverapi.client.classicLoginWithPassword
import de.connect2x.trixnity.clientserverapi.model.authentication.IdentifierType
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.m.room.MemberEventContent
import de.connect2x.trixnity.core.model.events.m.room.Membership
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.core.subscribeEvent
import io.ktor.http.Url
import kotlinx.coroutines.channels.SendChannel
import utils.ChatInterface
import utils.IncomingMessage
import utils.LogLevel
import utils.Logger

class MatrixBot(
    private val homeserver: String,
    private val username: String,
    private val password: String,
    private val logLevel: LogLevel = LogLevel.INFO
) : ChatInterface {
    override val label = "Matrix"
    private val log = Logger.getLogger(label, logLevel)

    private var client: MatrixClientServerApiClient? = null
    private var myUserId: UserId? = null
    private var lastRoomId: RoomId? = null

    override suspend fun start(sink: SendChannel<IncomingMessage>) {
        log.info("Connecting to $homeserver as $username...")

        Backend.set(DefaultBackend)

        val baseUrl = Url(homeserver)

        val authData = MatrixClientAuthProviderData.classicLoginWithPassword(
            baseUrl = baseUrl,
            identifier = IdentifierType.User(username),
            password = password
        ).getOrThrow()

        val matrixClient = MatrixClientServerApiClientImpl(
            authProvider = authData.createAuthProvider(
                MatrixClientAuthProviderDataStore.inMemory(authData)
            )
        )

        val whoami = matrixClient.authentication.whoAmI().getOrThrow()
        myUserId = whoami.userId
        log.info("Logged in as $myUserId")

        matrixClient.sync.subscribeEvent<MemberEventContent, ClientEvent.RoomEvent<MemberEventContent>> { event ->
            if (event.content.membership == Membership.INVITE) {
                log.info("Auto-joining room ${event.roomId}")
                matrixClient.room.joinRoom(event.roomId).onSuccess {
                    log.info("Joined ${event.roomId}")
                }.onFailure {
                    log.warn("Failed to join ${event.roomId}: ${it.message}")
                }
            }
        }

        matrixClient.sync.subscribeEvent<RoomMessageEventContent.TextBased, ClientEvent.RoomEvent<RoomMessageEventContent.TextBased>> { event ->
            val userId = myUserId
            if (userId != null && event.sender == userId) return@subscribeEvent
            val content = event.content
            if (content is RoomMessageEventContent.TextBased.Text) {
                lastRoomId = event.roomId
                log.info("Message from ${event.sender} in ${event.roomId}: ${content.body.take(80)}")
                sink.trySend(IncomingMessage(content.body, this@MatrixBot))
            }
        }

        matrixClient.sync.start()
        client = matrixClient

        log.info("Matrix bot connected and syncing")
    }

    override suspend fun stop() {
        log.info("Stopping Matrix bot...")
        client?.close()
        client = null
    }

    override suspend fun sendMessage(text: String) {
        val roomId = lastRoomId ?: run {
            log.warn("No room to reply to")
            return
        }
        client?.let { c ->
            c.room.sendMessageEvent(roomId, RoomMessageEventContent.TextBased.Text(body = text))
                .onFailure { log.warn("Failed to send message: ${it.message}") }
        }
    }
}
