package run.koto.desktop.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import run.koto.desktop.data.local.db.KotoDb
import run.koto.desktop.domain.model.Conversation
import run.koto.desktop.domain.model.ConversationType
import run.koto.desktop.domain.model.Folder
import run.koto.desktop.domain.repository.FolderRepository

/**
 * Local-only folder bookkeeping. Folders never leave the device — taxonomy
 * is personal, and the server doesn't need to know it exists. Cross-device
 * sync can come later via the encrypted-prefs path.
 */
class FolderRepositoryImpl(
    private val db: KotoDb,
) : FolderRepository {

    override fun observeFolders(): Flow<List<Folder>> =
        db.kotoDbQueries.selectAllFolders()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows ->
                rows.map { Folder(id = it.id, name = it.name, sortOrder = it.sort_order.toInt()) }
            }

    override fun observeConversations(folderId: Long): Flow<List<Conversation>> =
        db.kotoDbQueries.selectConversationsForFolder(folderId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun create(name: String): Long = withContext(Dispatchers.IO) {
        val trimmed = name.trim().take(40).ifBlank { return@withContext -1L }
        // The whole flow runs inside one transaction so the lastInsertId
        // call below reads the row we just wrote.
        var newId = -1L
        db.transaction {
            db.kotoDbQueries.insertFolder(name = trimmed, sort_order = nextSortOrder())
            newId = db.kotoDbQueries.lastInsertId().executeAsOne()
        }
        newId
    }

    override suspend fun rename(id: Long, name: String) {
        withContext(Dispatchers.IO) {
            db.kotoDbQueries.renameFolder(name = name.trim().take(40), id = id)
        }
    }

    override suspend fun delete(id: Long) {
        withContext(Dispatchers.IO) { db.kotoDbQueries.deleteFolder(id) }
    }

    override suspend fun addConversation(folderId: Long, conversationId: String) {
        withContext(Dispatchers.IO) {
            db.kotoDbQueries.addConvToFolder(folderId, conversationId)
        }
    }

    override suspend fun removeConversation(folderId: Long, conversationId: String) {
        withContext(Dispatchers.IO) {
            db.kotoDbQueries.removeConvFromFolder(folderId, conversationId)
        }
    }

    private fun nextSortOrder(): Long =
        (db.kotoDbQueries.selectAllFolders().executeAsList().maxOfOrNull { it.sort_order } ?: -1L) + 1L
}

// Mirror of the Conversation row → domain mapping in ConversationRepositoryImpl.
// Kept inline (rather than reused) so the folder repo doesn't have to depend
// on the conversation repo's internals — simpler than threading a shared mapper.
private fun run.koto.desktop.data.local.db.Conversation.toDomain() = Conversation(
    id              = id,
    type            = ConversationType.fromWire(type.toInt()),
    displayName     = display_name,
    peerAccountId   = peer_account_id,
    avatarUrl       = avatar_url,
    lastMessage     = last_message_preview,
    lastMessageTime = last_message_time,
    unreadCount     = unread_count.toInt(),
    isOnline        = is_online == 1L,
    isPinned        = is_pinned == 1L,
    isMuted         = is_muted  == 1L,
    isVerified      = is_verified == 1L,
    memberIds       = if (member_ids_csv.isBlank()) emptyList() else member_ids_csv.split(','),
)
