package run.koto.desktop.domain.repository

import kotlinx.coroutines.flow.Flow
import run.koto.desktop.domain.model.Message
import run.koto.desktop.domain.model.WebSocketEvent

interface ChatRepository {
    fun observeMessages(conversationId: String): Flow<List<Message>>
    fun observeEvents(): Flow<WebSocketEvent>

    suspend fun syncMessages(conversationId: String): Result<Unit>
    /** [replyToId] is the id of the message being quoted, or null for a
     *  brand-new top-level message. [forwardedFromAccountId] flags the
     *  message as a forward from someone else's account. */
    suspend fun send(
        conversationId         : String,
        plaintext              : String,
        replyToId              : String? = null,
        forwardedFromAccountId : String? = null,
    ): Result<Message>
    suspend fun markRead(conversationId: String, messageId: String)
    suspend fun delete(conversationId: String, messageId: String): Result<Unit>

    /** Re-encrypt [newPlaintext] and PATCH the existing message. Only the
     *  original sender can edit; the server enforces this. */
    suspend fun editMessage(conversationId: String, messageId: String, newPlaintext: String): Result<Unit>

    /** Forward [messageId] from [sourceConversationId] into [destinationConversationIds].
     *  The plaintext is read locally, then re-encrypted per destination's
     *  Signal session and sent. Returns the count of successful sends. */
    suspend fun forward(
        sourceConversationId   : String,
        messageId              : String,
        destinationConversationIds : List<String>,
    ): Result<Int>

    /** Pin or unpin a message. Updates local state immediately and PATCHes
     *  the server; the server fans out a WS event so peers update too. */
    suspend fun setPinned(conversationId: String, messageId: String, pinned: Boolean): Result<Unit>

    /** Toggle a single emoji reaction on a message. Returns whether the
     *  reaction is now ON (true) or OFF (false) after the toggle.
     *  Idempotent: calling twice with the same emoji is a no-op net effect. */
    suspend fun toggleReaction(conversationId: String, messageId: String, emoji: String): Result<Boolean>
}
