package run.koto.data.remote.ws

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import okhttp3.*
import org.json.JSONObject

sealed class WsEvent {
    data class NewMessage(
        val conversationId : String,
        val messageId      : String,
        val senderId       : String,
        val ciphertext     : String,
        val sentAt         : Long,
    ) : WsEvent()

    data class Delivered(
        val conversationId : String,
        val messageId      : String,
    ) : WsEvent()

    data class PresenceUpdate(
        val accountId : String,
        val online    : Boolean,
    ) : WsEvent()

    object Connected    : WsEvent()
    object Disconnected : WsEvent()
}

class KotoWebSocket(
    private val okHttpClient : OkHttp,
    private val wsUrl        : String,
) {
    private val _events = Channel<WsEvent>(Channel.BUFFERED)
    val events: Flow<WsEvent> = _events.receiveAsFlow()

    private var socket: WebSocket? = null

    fun connect(accessToken: String) {
        // Gateway authenticates via ?token= query param — headers are not accessible
        // on the server side after the WebSocket upgrade handshake in some stacks.
        val request = Request.Builder()
            .url("$wsUrl?token=$accessToken")
            .build()

        socket = okHttpClient.client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                _events.trySend(WsEvent.Connected)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                parseEvent(text)?.let { _events.trySend(it) }
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                _events.trySend(WsEvent.Disconnected)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                _events.trySend(WsEvent.Disconnected)
            }
        })
    }

    fun disconnect() {
        socket?.close(1000, "user disconnect")
        socket = null
    }

    private fun parseEvent(json: String): WsEvent? = runCatching {
        android.util.Log.d("KotoWS", "event raw: $json")
        val obj  = JSONObject(json)
        val type = obj.getString("type")

        // Gateway wraps payloads as { "type": "...", "payload": { ... } }.
        // Fall back to the top-level object so the parser works with both formats.
        val p = obj.optJSONObject("payload") ?: obj

        when (type) {
            "new_message" -> WsEvent.NewMessage(
                conversationId = p.getString("conversation_id"),
                messageId      = p.getString("message_id"),
                senderId       = p.getString("sender_id"),
                ciphertext     = p.getString("ciphertext"),
                sentAt         = p.getLong("sent_at"),
            )
            "delivered" -> WsEvent.Delivered(
                conversationId = p.getString("conversation_id"),
                messageId      = p.getString("message_id"),
            )
            "presence" -> WsEvent.PresenceUpdate(
                accountId = p.getString("account_id"),
                online    = p.getBoolean("online"),
            )
            else -> null
        }
    }.getOrNull()
}

// Thin wrapper so Hilt can inject OkHttpClient without naming collision.
// Accepts a lambda so the WebSocket picks up the Tor-proxied client dynamically.
class OkHttp(private val provider: () -> OkHttpClient) {
    constructor(fixedClient: OkHttpClient) : this({ fixedClient })
    val client: OkHttpClient get() = provider()
}
