package run.koto.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import run.koto.data.local.KotoDatabase
import run.koto.data.local.dao.ConversationDao
import run.koto.data.local.dao.MessageDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KotoDatabase =
        Room.databaseBuilder(context, KotoDatabase::class.java, "koto.db")
            .addMigrations(KotoDatabase.MIGRATION_1_2, KotoDatabase.MIGRATION_2_3)
            .fallbackToDestructiveMigration()   // dev safety net for any missed migrations
            .build()

    @Provides
    fun provideMessageDao(db: KotoDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideConversationDao(db: KotoDatabase): ConversationDao = db.conversationDao()
}
