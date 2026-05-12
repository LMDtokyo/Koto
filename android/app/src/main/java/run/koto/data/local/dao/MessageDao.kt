package run.koto.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import run.koto.data.local.entity.MessageEntity

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE conversation_id = :convId ORDER BY sent_at ASC")
    fun observeMessages(convId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversation_id = :convId ORDER BY sent_at ASC")
    suspend fun getMessages(convId: String): List<MessageEntity>


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(messages: List<MessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: MessageEntity)

    @Query("UPDATE messages SET delivered = 1 WHERE id = :msgId")
    suspend fun markDelivered(msgId: String)

    @Query("DELETE FROM messages WHERE id = :msgId")
    suspend fun delete(msgId: String)

    @Query("DELETE FROM messages WHERE conversation_id = :convId")
    suspend fun deleteForConversation(convId: String)

    @Query("DELETE FROM messages WHERE expires_at > 0 AND expires_at <= :now")
    suspend fun deleteExpired(now: Long)
}
