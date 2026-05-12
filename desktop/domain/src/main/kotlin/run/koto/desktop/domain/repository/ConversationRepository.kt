package run.koto.desktop.domain.repository

import kotlinx.coroutines.flow.Flow
import run.koto.desktop.domain.model.Conversation

interface ConversationRepository {
    fun observeAll(): Flow<List<Conversation>>
    fun observeArchived(): Flow<List<Conversation>>
    suspend fun sync(): Result<Unit>
    suspend fun createDirect(peerAccountId: String): Result<Conversation>
    suspend fun createGroup(name: String, memberAccountIds: List<String>): Result<Conversation>
    suspend fun setPinned(id: String, pinned: Boolean)
    suspend fun setMuted(id: String, muted: Boolean)
    suspend fun setArchived(id: String, archived: Boolean)
    suspend fun setVerified(id: String, verified: Boolean)
}
