package run.koto.desktop.domain.model

/**
 * Frames delivered over the gateway WebSocket. The payload shape matches the NATS
 * `chat.deliver.{convId}` envelope that the gateway forwards to subscribers.
 */
sealed interface WebSocketEvent {

    data class NewMessage(
        val messageId      : String,
        val conversationId : String,
        val senderId       : String,
        val sentAt         : Long,
        val ciphertext     : ByteArray,
        val replyToId      : String? = null,
        val forwardedFromAccountId : String? = null,
    ) : WebSocketEvent

    /** A message's ciphertext was replaced by its original sender. The
     *  receiver decrypts the new ciphertext, overwrites the cached plaintext
     *  for the existing [messageId], and stamps [editedAt] so the bubble
     *  can render an "(изменено)" badge. */
    data class MessageEdited(
        val conversationId : String,
        val messageId      : String,
        val senderId       : String,
        val ciphertext     : ByteArray,
        val editedAt       : Long,
    ) : WebSocketEvent

    /** A message was pinned or unpinned. The UI flips the pin badge on the
     *  bubble and adds/removes it from the pinned-bar at the top of the chat. */
    data class MessagePinned(
        val conversationId : String,
        val messageId      : String,
        val actorId        : String,
        val pinned         : Boolean,
    ) : WebSocketEvent

    data class Delivered(val conversationId: String, val messageId: String) : WebSocketEvent
    data class Read(val conversationId: String, val messageId: String)      : WebSocketEvent
    data class Typing(val conversationId: String, val senderId: String)     : WebSocketEvent
    data class PresenceUpdate(val accountId: String, val isOnline: Boolean) : WebSocketEvent

    /** A new conversation (typically a group) the user is now a member of.
     *  The gateway forwards this from the chat service's `conversation.created`
     *  NATS topic so other members' clients update without polling. */
    data class ConversationCreated(
        val conversationId : String,
        val type           : ConversationType,
        val name           : String,
        val creatorId      : String,
        val memberIds      : List<String>,
        val createdAt      : Long,
    ) : WebSocketEvent

    /** A peer added or removed an emoji reaction on a message. `added=false`
     *  means the same actor toggled the same emoji off — receivers should
     *  drop their local row matching (messageId, actorId, emoji). */
    data class ReactionToggled(
        val conversationId : String,
        val messageId      : String,
        val actorId        : String,
        val emoji          : String,
        val added          : Boolean,
        val reactedAt      : Long,
    ) : WebSocketEvent

    data object Connected    : WebSocketEvent
    data object Disconnected : WebSocketEvent
}
