package run.koto.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * @Immutable UI model classes for KotoTheme downstream composables.
 *
 * DS-07: All data classes must be @Immutable with ImmutableList for collection fields.
 *
 * @Immutable contract: no property ever changes after construction — callers
 * replace the object via copy() to update state. This allows the Compose compiler
 * to skip recomposition when the object reference has not changed.
 *
 * Rule: NEVER use List<T> in an @Immutable class — use ImmutableList<T> from
 * kotlinx.collections.immutable. Standard Kotlin List<T> is treated as unstable
 * by the compiler regardless of @Immutable annotation.
 *
 * Migration: MessageUi.kt and ConversationUi.kt are legacy files kept for reference.
 * New code should use the canonical definitions in this file.
 */

// ─── Message status ────────────────────────────────────────────────────────────

enum class MessageStatus {
    SENDING,    // queued locally, not yet sent to server
    SENT,       // received by server (single gray check)
    DELIVERED,  // delivered to recipient device (double gray check)
    READ,       // read by recipient (double colored check)
    FAILED,     // send failed — retry available
}

// ─── Message UI model ─────────────────────────────────────────────────────────

/**
 * Immutable UI representation of a single message.
 * Passed to MessageBubble, MessageRow composables.
 * Canonical definition — supersedes run.koto.domain.model.MessageUi (legacy).
 */
@Immutable
data class MessageUi(
    val id         : String,
    val senderId   : String,
    val text       : String,
    val sentAt     : Long,
    val status     : MessageStatus,
    val isOutgoing : Boolean,
    val replyTo    : MessageUi?    = null,
    val mediaUrl   : String?       = null,
    val blurHash   : String?       = null,
)

// ─── Conversation UI model ─────────────────────────────────────────────────────

/**
 * Immutable UI representation of a conversation row in the conversation list.
 * Passed to ConversationRow composable.
 * Canonical definition — supersedes run.koto.domain.model.ConversationUi (legacy).
 */
@Immutable
data class ConversationUi(
    val id          : String,
    val displayName : String,
    /** Peer's stable account ID (empty for group chats). Used as the seed for
     *  avatar colours so the same person is always painted the same gradient. */
    val peerId      : String,
    val avatarUrl   : String?,
    val lastMessage : String,
    val unreadCount : Int,
    val isOnline    : Boolean,
    val isPinned    : Boolean,
    val isMuted     : Boolean,
    val updatedAt   : Long,
)

// ─── Screen state objects ──────────────────────────────────────────────────────

/**
 * Immutable state for the chat screen ViewModel.
 * ImmutableList<MessageUi> ensures the Compose compiler treats this as stable.
 */
@Immutable
data class ChatState(
    val messages    : ImmutableList<MessageUi> = persistentListOf(),
    val isLoading   : Boolean                  = false,
    val isTyping    : Boolean                  = false,
    val displayName : String                   = "",
    /** Peer's stable account ID — seed for the avatar gradient. */
    val peerId      : String                   = "",
    val avatarUrl   : String?                  = null,
    val inputText   : String                   = "",
    val sending     : Boolean                  = false,
    val online      : Boolean                  = false,
)

// ─── Chat item sealed type (replaces MsgRender) ────────────────────────────────

/**
 * Sealed type for items rendered in the chat LazyColumn.
 * Computed in ChatItemMapper.buildChatItems() on Dispatchers.Default — never in composition.
 * contentType values: 0=DateSeparator, 1=text message, 2=media message.
 */
@Immutable
sealed interface ChatItem {
    val key: String

    @Immutable
    data class Message(
        val msg           : MessageUi,
        val showTail      : Boolean,
        val showAvatar    : Boolean,
        val formattedTime : String,   // pre-computed — no SimpleDateFormat in composition
    ) : ChatItem {
        override val key: String get() = msg.id
        val contentType: Int get() = if (msg.mediaUrl != null) 2 else 1
    }

    @Immutable
    data class DateSeparator(
        val label: String,            // "Сегодня", "Вчера", "5 апреля"
    ) : ChatItem {
        override val key: String get() = "date-$label"
        val contentType: Int get() = 0
    }
}

/**
 * Immutable state for the conversation list screen ViewModel.
 */
@Immutable
data class ConversationListState(
    val conversations : ImmutableList<ConversationUi> = persistentListOf(),
    val pinnedConvs   : ImmutableList<ConversationUi> = persistentListOf(),
    val isLoading     : Boolean                       = false,
    val searchQuery   : String                        = "",
)
