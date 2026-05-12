package run.koto.desktop.domain.repository

import run.koto.desktop.domain.model.StorageInfo

interface StorageRepository {
    /** Compute current disk usage and per-conversation breakdown. */
    suspend fun snapshot(): StorageInfo

    /** Wipe the on-disk image cache directory. SQLite is left intact. */
    suspend fun clearMediaCache()

    /** Drop every locally stored message + image, then VACUUM the SQLite file. */
    suspend fun clearAllMessages()

    /** Drop messages for a single conversation (chat row stays). */
    suspend fun clearConversationMessages(conversationId: String)
}
