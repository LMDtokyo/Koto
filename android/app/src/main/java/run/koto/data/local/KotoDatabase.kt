package run.koto.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import run.koto.data.local.dao.ConversationDao
import run.koto.data.local.dao.MessageDao
import run.koto.data.local.entity.ConversationEntity
import run.koto.data.local.entity.MessageEntity

@Database(
    entities     = [
        MessageEntity::class,
        ConversationEntity::class,
    ],
    version      = 3,
    exportSchema = false,
)
abstract class KotoDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun conversationDao(): ConversationDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE messages ADD COLUMN expires_at INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE conversations ADD COLUMN peer_account_id TEXT NOT NULL DEFAULT ''"
                )
            }
        }
    }
}
