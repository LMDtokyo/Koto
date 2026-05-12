package run.koto.desktop.domain.model

data class Message(
    val id             : String,
    val conversationId : String,
    val senderId       : String,
    val plaintext      : String,
    val sentAt         : Long,
    val delivered      : Boolean,
    val read           : Boolean,
    val expiresAt      : Long?,
    val isOutgoing     : Boolean,
    /** Id of the message this one is replying to. UI resolves it locally to
     *  render the quoted bubble; null when this isn't a reply. */
    val replyToId      : String? = null,
    /** Last edit time in epoch millis. Null when the message has never been
     *  edited; UI renders an "(изменено)" label otherwise. */
    val editedAt       : Long?   = null,
    /** Account id of the original author when this message is a forward.
     *  Null when typed directly. UI renders "Переслано от …" above the text. */
    val forwardedFrom  : String? = null,
    /** True when the message is currently pinned in its conversation. */
    val pinned         : Boolean = false,
    val reactions      : List<MessageReaction> = emptyList(),
)

/** A single emoji reaction on a message. Multiple actors can react with the
 *  same emoji — the UI groups them into a chip with `count` and a
 *  `mineEmoji` flag derived from whether the local user is in [actorIds]. */
data class MessageReaction(
    val messageId : String,
    val actorId   : String,
    val emoji     : String,
    val reactedAt : Long,
)
