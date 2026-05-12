package run.koto.desktop.data.ws

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.slf4j.LoggerFactory
import run.koto.desktop.crypto.KotoCryptoProvider
import run.koto.desktop.data.local.db.KotoDb
import run.koto.desktop.domain.model.ConversationType
import run.koto.desktop.domain.model.WebSocketEvent
import run.koto.desktop.domain.repository.ProfileRepository

/**
 * Applies gateway WS frames to local state.
 *
 *   NewMessage      → decrypt ciphertext with [KotoCryptoProvider]; persist a row in the
 *                     `message` table. The `observeMessages` flow emits automatically via
 *                     SQLDelight change notifications so ViewModels update without any
 *                     explicit observer glue.
 *   Delivered       → flip `delivered` bit for the matching row.
 *   Read            → stamp `read_at` so read receipts are visible locally.
 *   PresenceUpdate  → flip `is_online` on all conversations whose peer matches.
 *   Typing          → ephemeral, not stored; UI subscribes to the raw flow for these.
 */
class WebSocketEventDispatcher(
    private val events  : SharedFlow<WebSocketEvent>,
    private val db      : KotoDb,
    private val crypto  : KotoCryptoProvider,
    private val profile : ProfileRepository,
) {
    private val log = LoggerFactory.getLogger(WebSocketEventDispatcher::class.java)

    fun start(scope: CoroutineScope): Job =
        events.onEach(::handle).launchIn(scope)

    private suspend fun handle(event: WebSocketEvent) {
        when (event) {
            is WebSocketEvent.NewMessage          -> onNewMessage(event)
            is WebSocketEvent.Delivered           -> db.kotoDbQueries.markDelivered(event.messageId)
            is WebSocketEvent.Read                -> db.kotoDbQueries.markRead(System.currentTimeMillis(), event.messageId)
            is WebSocketEvent.PresenceUpdate      -> db.kotoDbQueries.setOnline(if (event.isOnline) 1 else 0, event.accountId)
            is WebSocketEvent.ConversationCreated -> onConversationCreated(event)
            is WebSocketEvent.ReactionToggled     -> onReactionToggled(event)
            is WebSocketEvent.MessageEdited       -> onMessageEdited(event)
            is WebSocketEvent.MessagePinned       -> db.kotoDbQueries.setMessagePinned(if (event.pinned) 1 else 0, event.messageId)
            is WebSocketEvent.Typing,
            WebSocketEvent.Connected,
            WebSocketEvent.Disconnected           -> Unit
        }
    }

    private suspend fun onMessageEdited(event: WebSocketEvent.MessageEdited) {
        // Decrypt the new ciphertext under the existing Signal session — the
        // session ratchets per-message so a fresh ciphertext from the same
        // sender is just another message in the chain. We then overwrite the
        // cached plaintext for the existing message id; the row's UI flips
        // to "(изменено)" via the editedAt stamp.
        val plaintext = if (event.ciphertext.isEmpty()) {
            PLACEHOLDER_ENCRYPTED
        } else {
            crypto.decrypt(event.senderId, event.ciphertext)
                .getOrNull()
                ?.toString(Charsets.UTF_8)
                ?: PLACEHOLDER_ENCRYPTED
        }
        db.kotoDbQueries.editMessage(
            plaintext = plaintext,
            ciphertext = event.ciphertext,
            editedAt = event.editedAt.takeIf { it > 0L }?.let { it * 1000 } ?: System.currentTimeMillis(),
            id = event.messageId,
        )
        log.debug("applied message_edited msg={} conv={}", event.messageId, event.conversationId)
    }

    private fun onReactionToggled(event: WebSocketEvent.ReactionToggled) {
        if (event.added) {
            db.kotoDbQueries.upsertReaction(
                message_id = event.messageId,
                actor_id   = event.actorId,
                emoji      = event.emoji,
                reacted_at = event.reactedAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
            )
        } else {
            db.kotoDbQueries.deleteReaction(event.messageId, event.actorId, event.emoji)
        }
        log.debug(
            "reaction {} msg={} actor={} emoji={}",
            if (event.added) "added" else "removed",
            event.messageId, event.actorId, event.emoji,
        )
    }

    private suspend fun onConversationCreated(event: WebSocketEvent.ConversationCreated) {
        // Skip if we already know about this conv — likely we're the creator,
        // we already inserted it locally, and the gateway just echoed our
        // own broadcast back via another device.
        val existing = db.kotoDbQueries.selectConversationById(event.conversationId).executeAsOneOrNull()
        if (existing != null) {
            log.debug("conversation_created ignored for known conv={}", event.conversationId)
            return
        }

        // For direct chats from a remote creator, label by their profile.
        // For groups, the server-supplied name is authoritative; fall back to
        // a "Группа · N участников" placeholder when the creator left it blank.
        val (displayName, peerAnchor) = when (event.type) {
            ConversationType.Direct -> {
                val peerId = event.creatorId
                val name   = runCatching { profile.get(peerId).getOrNull()?.displayName }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: peerId
                name to peerId
            }
            ConversationType.Group  -> {
                val n = event.name.ifBlank { "Группа · ${event.memberIds.size} участника" }
                n to event.creatorId
            }
        }

        db.kotoDbQueries.upsertConversation(
            id                   = event.conversationId,
            type                 = event.type.wire.toLong(),
            display_name         = displayName,
            peer_account_id      = peerAnchor,
            avatar_url           = null,
            last_message_preview = "",
            last_message_time    = event.createdAt * 1000,
            unread_count         = 0,
            is_online            = 0,
            is_pinned            = 0,
            is_muted             = 0,
            is_archived          = 0,
            is_verified          = 0,
            member_ids_csv       = event.memberIds.joinToString(","),
        )
        log.info(
            "conversation_created applied conv={} type={} members={}",
            event.conversationId, event.type, event.memberIds.size,
        )
    }

    private suspend fun onNewMessage(event: WebSocketEvent.NewMessage) {
        val plaintext = if (event.ciphertext.isEmpty()) {
            PLACEHOLDER_ENCRYPTED
        } else {
            crypto.decrypt(event.senderId, event.ciphertext)
                .getOrNull()
                ?.toString(Charsets.UTF_8)
                ?: PLACEHOLDER_ENCRYPTED
        }

        db.kotoDbQueries.insertMessage(
            id              = event.messageId,
            conversation_id = event.conversationId,
            sender_id       = event.senderId,
            plaintext_cache = plaintext,
            ciphertext      = event.ciphertext,
            sent_at         = event.sentAt,
            delivered       = 1,
            read_at         = null,
            expires_at      = null,
            reply_to        = event.replyToId,
            edited_at       = null,
            forwarded_from  = event.forwardedFromAccountId,
            pinned          = 0,
        )
        log.debug("applied new_message msg={} conv={} size={}", event.messageId, event.conversationId, event.ciphertext.size)
    }

    companion object {
        private const val PLACEHOLDER_ENCRYPTED = "[encrypted]"
    }
}
