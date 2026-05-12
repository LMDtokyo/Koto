package run.koto.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import run.koto.data.local.entity.ConversationEntity

@Dao
interface ConversationDao {

    @Query("SELECT * FROM conversations ORDER BY CASE WHEN last_message_time IS NULL THEN 0 ELSE last_message_time END DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getById(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(conversations: List<ConversationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conversation: ConversationEntity)

    @Query("""
        UPDATE conversations
        SET last_message_preview = :preview, last_message_time = :time
        WHERE id = :convId
    """)
    suspend fun updateLastMessage(convId: String, preview: String, time: Long)

    @Query("UPDATE conversations SET unread_count = 0 WHERE id = :convId")
    suspend fun clearUnread(convId: String)

    @Query("UPDATE conversations SET online = :online WHERE id = :accountId")
    suspend fun updateOnline(accountId: String, online: Boolean)
}
