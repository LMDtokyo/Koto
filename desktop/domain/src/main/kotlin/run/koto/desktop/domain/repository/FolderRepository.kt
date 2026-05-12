package run.koto.desktop.domain.repository

import kotlinx.coroutines.flow.Flow
import run.koto.desktop.domain.model.Conversation
import run.koto.desktop.domain.model.Folder

interface FolderRepository {
    fun observeFolders(): Flow<List<Folder>>
    fun observeConversations(folderId: Long): Flow<List<Conversation>>
    suspend fun create(name: String): Long
    suspend fun rename(id: Long, name: String)
    suspend fun delete(id: Long)
    suspend fun addConversation(folderId: Long, conversationId: String)
    suspend fun removeConversation(folderId: Long, conversationId: String)
}
