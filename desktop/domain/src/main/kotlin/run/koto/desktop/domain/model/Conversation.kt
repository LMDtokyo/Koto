package run.koto.desktop.domain.model

/** Distinguishes a 1:1 conversation from a multi-member group. Mirrors the
 *  server's `ConversationType` enum (1 = direct, 2 = group). */
enum class ConversationType(val wire: Int) {
    Direct(1),
    Group(2);

    companion object {
        fun fromWire(value: Int): ConversationType = when (value) {
            2    -> Group
            else -> Direct
        }
    }
}

data class Conversation(
    val id              : String,
    val type            : ConversationType = ConversationType.Direct,
    val displayName     : String,
    val peerAccountId   : String,
    val avatarUrl       : String?,
    val lastMessage     : String,
    val lastMessageTime : Long,
    val unreadCount     : Int,
    val isOnline        : Boolean,
    val isPinned        : Boolean,
    val isMuted         : Boolean,
    val isVerified      : Boolean = false,
    /** Only meaningful for groups — empty list for direct chats. */
    val memberIds       : List<String> = emptyList(),
)
