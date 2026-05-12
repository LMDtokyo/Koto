package run.koto.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import run.koto.domain.model.MessageUi

@Entity(
    tableName = "messages",
    indices   = [Index("conversation_id"), Index("sent_at")],
)
data class MessageEntity(
    @PrimaryKey val id                          : String,
    @ColumnInfo(name = "conversation_id")
    val conversationId                          : String,
    val ciphertext                              : String,
    @ColumnInfo(name = "plaintext_cache")
    val plaintextCache                          : String,
    @ColumnInfo(name = "sender_id")
    val senderId                                : String,
    @ColumnInfo(name = "my_account_id")
    val myAccountId                             : String,
    @ColumnInfo(name = "sent_at")
    val sentAt                                  : Long,
    val delivered                               : Boolean,
    @ColumnInfo(name = "expires_at", defaultValue = "0")
    val expiresAt                               : Long = 0L,
)

fun MessageEntity.toUi() = MessageUi(
    id         = id,
    senderId   = senderId,
    text       = plaintextCache,
    sentAt     = sentAt,
    status     = if (delivered) run.koto.domain.model.MessageStatus.DELIVERED
                 else run.koto.domain.model.MessageStatus.SENT,
    isOutgoing = senderId == myAccountId,
)
