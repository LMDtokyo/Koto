package run.koto.desktop.data.ws

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.HttpMethod
import io.ktor.http.URLProtocol
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import run.koto.desktop.domain.DomainError
import run.koto.desktop.domain.model.ConversationType
import run.koto.desktop.domain.model.WebSocketEvent
import java.util.Base64
import kotlin.coroutines.coroutineContext
import kotlin.math.min
import kotlin.random.Random

/**
 * Gateway WebSocket client.
 *
 * Auth: access token is passed via `?token=` query param. After the HTTP→WS upgrade the
 * server-side request headers are not always visible to the upgraded handler in the gateway's
 * stack, so query-string is the established contract (matches the Android client).
 *
 * Token rotation is handled inside the loop — each reconnect re-reads from
 * [tokenProvider], and a 401 (mapped to [DomainError.Unauthorized] by the
 * HTTP response validator) triggers [onAuthFail] which is expected to refresh
 * the session. Returning false from [onAuthFail] tears the loop down so the
 * UI can drop back to the auth flow.
 *
 * Reconnect: exponential backoff with jitter, capped at 30s. Cancels cleanly on [disconnect].
 * The raw [Frame] stream is parsed into typed [WebSocketEvent]s; the app-layer dispatcher
 * ([WebSocketEventDispatcher]) owns DB reconciliation.
 */
class KotoWebSocket(
    private val http          : HttpClient,
    private val wsHost        : String,
    private val wsPort        : Int,
    private val useTls        : Boolean,
    private val tokenProvider : suspend () -> String?,
    private val onAuthFail    : suspend () -> Boolean,
) {
    private val log = LoggerFactory.getLogger(KotoWebSocket::class.java)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _events = MutableSharedFlow<WebSocketEvent>(
        extraBufferCapacity = 256,
        onBufferOverflow    = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<WebSocketEvent> = _events.asSharedFlow()

    @Volatile private var loop : Job? = null

    fun connect(scope: CoroutineScope) {
        loop?.cancel()
        loop = scope.launch { runLoop() }
    }

    fun disconnect() {
        loop?.cancel()
        loop = null
    }

    private suspend fun runLoop() {
        var attempt = 0
        while (coroutineContext.isActive) {
            val token = tokenProvider() ?: run {
                log.debug("no access token, ws loop idle")
                return
            }
            try {
                http.webSocket(
                    method = HttpMethod.Get,
                    host   = wsHost,
                    port   = wsPort,
                    path   = "/ws",
                    request = {
                        url.protocol = if (useTls) URLProtocol.WSS else URLProtocol.WS
                        url.parameters.append("token", token)
                    },
                ) {
                    attempt = 0
                    log.info("websocket connected host={} tls={}", wsHost, useTls)
                    _events.emit(WebSocketEvent.Connected)
                    while (currentCoroutineContext().isActive) {
                        val frame = incoming.receive()
                        if (frame !is Frame.Text) continue
                        parse(frame.readText())?.let { _events.emit(it) }
                    }
                }
            } catch (_: CancellationException) {
                log.debug("websocket loop cancelled")
                _events.emit(WebSocketEvent.Disconnected)
                return
            } catch (e: DomainError.Unauthorized) {
                log.info("websocket auth rejected, requesting refresh")
                _events.emit(WebSocketEvent.Disconnected)
                if (!onAuthFail()) {
                    log.warn("refresh failed permanently, dropping ws loop")
                    return
                }
                attempt = 0
            } catch (e: Throwable) {
                log.warn("websocket disconnected, scheduling reconnect: {}", e.message)
                _events.emit(WebSocketEvent.Disconnected)
                delay(backoffFor(attempt++))
            }
        }
    }

    private fun backoffFor(attempt: Int): Long {
        val base  = min(1_000L shl min(attempt, 5), MAX_BACKOFF_MS)
        val jitter = Random.nextLong(0, base / 2 + 1)
        return base / 2 + jitter
    }

    private fun parse(raw: String): WebSocketEvent? {
        val root    = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
        val type    = root["type"]?.jsonPrimitive?.contentOrNull ?: return null
        val payload = root["payload"] as? JsonObject ?: root
        return when (type) {
            "new_message"          -> payload.toNewMessage()
            "delivered"            -> payload.toDelivered()
            "read"                 -> payload.toRead()
            "typing"               -> payload.toTyping()
            "presence"             -> payload.toPresence()
            "conversation_created" -> payload.toConversationCreated()
            "reaction"             -> payload.toReactionToggled()
            "message_edited"       -> payload.toMessageEdited()
            "message_pinned"       -> payload.toMessagePinned()
            else                   -> null.also { log.debug("unknown ws frame type={}", type) }
        }
    }

    private fun JsonObject.toMessagePinned(): WebSocketEvent.MessagePinned? {
        val convId    = this["conversation_id"]?.jsonPrimitive?.contentOrNull
            ?: this["conv_id"]?.jsonPrimitive?.contentOrNull
            ?: return null
        val messageId = this["message_id"]?.jsonPrimitive?.contentOrNull
            ?: this["msg_id"]?.jsonPrimitive?.contentOrNull
            ?: return null
        val actorId   = this["actor_id"]?.jsonPrimitive?.contentOrNull ?: return null
        val pinned    = this["pinned"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: return null
        return WebSocketEvent.MessagePinned(
            conversationId = convId,
            messageId      = messageId,
            actorId        = actorId,
            pinned         = pinned,
        )
    }

    private fun JsonObject.toMessageEdited(): WebSocketEvent.MessageEdited? {
        val convId    = this["conversation_id"]?.jsonPrimitive?.contentOrNull
            ?: this["conv_id"]?.jsonPrimitive?.contentOrNull
            ?: return null
        val messageId = this["message_id"]?.jsonPrimitive?.contentOrNull
            ?: this["msg_id"]?.jsonPrimitive?.contentOrNull
            ?: return null
        val sender    = this["sender_id"]?.jsonPrimitive?.contentOrNull ?: return null
        val editedAt  = this["edited_at"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
        val cipher    = this["ciphertext"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val cipherBytes = runCatching { Base64.getDecoder().decode(cipher) }.getOrElse { ByteArray(0) }
        return WebSocketEvent.MessageEdited(
            conversationId = convId,
            messageId      = messageId,
            senderId       = sender,
            ciphertext     = cipherBytes,
            editedAt       = editedAt,
        )
    }

    private fun JsonObject.toReactionToggled(): WebSocketEvent.ReactionToggled? {
        val convId    = this["conversation_id"]?.jsonPrimitive?.contentOrNull
            ?: this["conv_id"]?.jsonPrimitive?.contentOrNull
            ?: return null
        val messageId = this["message_id"]?.jsonPrimitive?.contentOrNull
            ?: this["msg_id"]?.jsonPrimitive?.contentOrNull
            ?: return null
        val actorId   = this["actor_id"]?.jsonPrimitive?.contentOrNull ?: return null
        val emoji     = this["emoji"]?.jsonPrimitive?.contentOrNull   ?: return null
        val added     = this["added"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: return null
        val reactedAt = this["reacted_at"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
        return WebSocketEvent.ReactionToggled(
            conversationId = convId,
            messageId      = messageId,
            actorId        = actorId,
            emoji          = emoji,
            added          = added,
            reactedAt      = reactedAt,
        )
    }

    private fun JsonObject.toConversationCreated(): WebSocketEvent.ConversationCreated? {
        val convId    = this["conversation_id"]?.jsonPrimitive?.contentOrNull ?: return null
        val typeWire  = this["type"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1
        val name      = this["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val creatorId = this["creator_id"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val createdAt = this["created_at"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
        val members   = (this["member_ids"] as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            .orEmpty()
        return WebSocketEvent.ConversationCreated(
            conversationId = convId,
            type           = ConversationType.fromWire(typeWire),
            name           = name,
            creatorId      = creatorId,
            memberIds      = members,
            createdAt      = createdAt,
        )
    }

    private fun JsonObject.toNewMessage(): WebSocketEvent.NewMessage? {
        val convId    = this["conversation_id"]?.jsonPrimitive?.contentOrNull
            ?: this["conv_id"]?.jsonPrimitive?.contentOrNull
            ?: return null
        val messageId = this["message_id"]?.jsonPrimitive?.contentOrNull
            ?: this["msg_id"]?.jsonPrimitive?.contentOrNull
            ?: return null
        val sender    = this["sender_id"]?.jsonPrimitive?.contentOrNull ?: return null
        val sentAt    = this["sent_at"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: return null
        val cipher    = this["ciphertext"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val cipherBytes = runCatching { Base64.getDecoder().decode(cipher) }.getOrElse { ByteArray(0) }
        val replyTo   = this["reply_to"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val forwarded = this["forwarded_from"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        return WebSocketEvent.NewMessage(
            messageId               = messageId,
            conversationId          = convId,
            senderId                = sender,
            sentAt                  = sentAt,
            ciphertext              = cipherBytes,
            replyToId               = replyTo,
            forwardedFromAccountId  = forwarded,
        )
    }

    private fun JsonObject.toDelivered(): WebSocketEvent.Delivered? {
        val convId = this["conversation_id"]?.jsonPrimitive?.contentOrNull ?: return null
        val msgId  = this["message_id"]?.jsonPrimitive?.contentOrNull     ?: return null
        return WebSocketEvent.Delivered(convId, msgId)
    }

    private fun JsonObject.toRead(): WebSocketEvent.Read? {
        val convId = this["conversation_id"]?.jsonPrimitive?.contentOrNull ?: return null
        val msgId  = this["message_id"]?.jsonPrimitive?.contentOrNull     ?: return null
        return WebSocketEvent.Read(convId, msgId)
    }

    private fun JsonObject.toTyping(): WebSocketEvent.Typing? {
        val convId = this["conversation_id"]?.jsonPrimitive?.contentOrNull ?: return null
        val sender = this["sender_id"]?.jsonPrimitive?.contentOrNull      ?: return null
        return WebSocketEvent.Typing(convId, sender)
    }

    private fun JsonObject.toPresence(): WebSocketEvent.PresenceUpdate? {
        val acc    = this["account_id"]?.jsonPrimitive?.contentOrNull ?: return null
        val online = this["online"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: return null
        return WebSocketEvent.PresenceUpdate(acc, online)
    }

    companion object {
        private const val MAX_BACKOFF_MS = 30_000L
    }
}
