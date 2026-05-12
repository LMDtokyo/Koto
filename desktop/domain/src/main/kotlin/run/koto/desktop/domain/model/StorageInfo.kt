package run.koto.desktop.domain.model

/**
 * Snapshot of how much disk space Koto's local data occupies. Computed on demand
 * — the screen recomputes after any clear action, so there is no observable
 * Flow contract for this.
 */
data class StorageInfo(
    val dbBytes              : Long,
    val cacheBytes           : Long,
    val totalBytes           : Long,
    val perConversation      : List<ConversationStorage>,
    val totalMessageCount    : Long,
)

/**
 * Per-conversation storage breakdown — sorted by [bytes] descending so the heaviest
 * chats float to the top, mirroring Telegram's "Manage Storage" list.
 */
data class ConversationStorage(
    val conversationId : String,
    val displayName    : String,
    val peerAccountId  : String,
    val avatarUrl      : String?,
    val messageCount   : Long,
    val bytes          : Long,
)
