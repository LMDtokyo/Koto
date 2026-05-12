package run.koto.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import run.koto.domain.model.ConversationInfo
import run.koto.domain.model.ConversationUi

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id                          : String,
    @ColumnInfo(name = "display_name")
    val displayName                             : String,
    @ColumnInfo(name = "last_message_preview")
    val lastMessagePreview                      : String?,
    @ColumnInfo(name = "last_message_time")
    val lastMessageTime                         : Long?,
    @ColumnInfo(name = "unread_count")
    val unreadCount                             : Int,
    val online                                  : Boolean,
    // For direct conversations: the other participant's account ID (used for E2E encryption).
    @ColumnInfo(name = "peer_account_id", defaultValue = "")
    val peerAccountId                           : String = "",
)

fun ConversationEntity.toUi() = ConversationUi(
    id          = id,
    displayName = displayName,
    peerId      = peerAccountId,
    avatarUrl   = null,
    lastMessage = lastMessagePreview.orEmpty(),
    unreadCount = unreadCount,
    isOnline    = online,
    isPinned    = false,
    isMuted     = false,
    updatedAt   = lastMessageTime ?: 0L,
)

fun ConversationEntity.toInfo() = ConversationInfo(
    displayName = displayName,
    peerId      = peerAccountId,
    online      = online,
)
