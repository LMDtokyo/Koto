package run.koto.desktop.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import run.koto.desktop.data.local.db.KotoDb
import run.koto.desktop.data.remote.api.ChatApi
import run.koto.desktop.data.remote.api.UserApi
import run.koto.desktop.domain.model.Conversation
import run.koto.desktop.domain.model.ConversationType
import run.koto.desktop.domain.repository.ConversationRepository
import java.util.Base64

class ConversationRepositoryImpl(
    private val chatApi : ChatApi,
    private val userApi : UserApi,
    private val db      : KotoDb,
) : ConversationRepository {

    private val log = LoggerFactory.getLogger(ConversationRepositoryImpl::class.java)

    override fun observeAll(): Flow<List<Conversation>> =
        db.kotoDbQueries.selectAllConversations()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeArchived(): Flow<List<Conversation>> =
        db.kotoDbQueries.selectArchivedConversations()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun sync(): Result<Unit> = runCatching {
        val dtos = withContext(Dispatchers.IO) { chatApi.listConversations() }
        dtos.forEach { dto ->
            val type = ConversationType.fromWire(dto.type)

            // For direct convs we look up the peer profile to get a friendly
            // display name + avatar. Groups carry their own server-supplied
            // name and have no single peer profile — reuse the first member
            // as an avatar accent anchor.
            val profile = if (type == ConversationType.Direct && dto.peer_id.isNotBlank()) {
                runCatching { userApi.getProfile(dto.peer_id) }.getOrNull()
            } else null

            val displayName = when (type) {
                ConversationType.Group  -> dto.name.ifBlank { dto.display_name }.ifBlank {
                    "Группа · ${dto.member_ids.size} участника"
                }
                ConversationType.Direct -> profile?.display_name?.ifBlank { null }
                    ?: dto.display_name.ifBlank { null }
                    ?: DEFAULT_PEER_NAME
            }
            val peerAnchor = when (type) {
                ConversationType.Group  -> dto.member_ids.firstOrNull().orEmpty()
                ConversationType.Direct -> dto.peer_id
            }
            val avatar = profile?.avatar_url?.ifBlank { null }

            val preview = dto.last_message?.ciphertext?.let(::base64PreviewLen).orEmpty()
            val time    = dto.last_message?.sent_at ?: 0L

            val existing = db.kotoDbQueries.selectConversationById(dto.id).executeAsOneOrNull()

            db.kotoDbQueries.upsertConversation(
                id                   = dto.id,
                type                 = type.wire.toLong(),
                display_name         = displayName,
                peer_account_id      = peerAnchor,
                avatar_url           = avatar,
                last_message_preview = preview.ifBlank { existing?.last_message_preview.orEmpty() },
                last_message_time    = if (time > 0) time else (existing?.last_message_time ?: 0),
                unread_count         = dto.unread_count.toLong(),
                is_online            = if (dto.online) 1 else 0,
                is_pinned            = existing?.is_pinned ?: 0,
                is_muted             = existing?.is_muted  ?: 0,
                is_archived          = existing?.is_archived ?: 0,
                is_verified          = existing?.is_verified ?: 0,
                member_ids_csv       = dto.member_ids.joinToString(","),
            )
        }
        log.debug("synced {} conversations", dtos.size)
    }.onFailure { log.warn("sync conversations failed", it) }

    override suspend fun createDirect(peerAccountId: String): Result<Conversation> = runCatching {
        val created = withContext(Dispatchers.IO) { chatApi.createDirect(peerAccountId) }
        val profile = runCatching { userApi.getProfile(peerAccountId) }.getOrNull()
        val name    = profile?.display_name?.ifBlank { null } ?: DEFAULT_PEER_NAME

        db.kotoDbQueries.upsertConversation(
            id                   = created.conversation_id,
            type                 = ConversationType.Direct.wire.toLong(),
            display_name         = name,
            peer_account_id      = peerAccountId,
            avatar_url           = profile?.avatar_url?.ifBlank { null },
            last_message_preview = "",
            last_message_time    = 0,
            unread_count         = 0,
            is_online            = 0,
            is_pinned            = 0,
            is_muted             = 0,
            is_archived          = 0,
            is_verified          = 0,
            member_ids_csv       = created.member_ids.joinToString(","),
        )
        Conversation(
            id              = created.conversation_id,
            type            = ConversationType.Direct,
            displayName     = name,
            peerAccountId   = peerAccountId,
            avatarUrl       = profile?.avatar_url?.ifBlank { null },
            lastMessage     = "",
            lastMessageTime = 0,
            unreadCount     = 0,
            isOnline        = false,
            isPinned        = false,
            isMuted         = false,
            isVerified      = false,
            memberIds       = created.member_ids,
        )
    }.onFailure { log.warn("createDirect failed peer={}", peerAccountId, it) }

    override suspend fun createGroup(name: String, memberAccountIds: List<String>): Result<Conversation> = runCatching {
        require(memberAccountIds.isNotEmpty()) { "group must have at least one member" }
        val created = withContext(Dispatchers.IO) { chatApi.createGroup(memberAccountIds, name.trim()) }
        val displayName = name.trim().ifBlank { "Группа · ${memberAccountIds.size + 1} участника" }
        // The server echoes back the canonical membership including the
        // creator (us); fall back to what we sent if it's missing for any
        // reason so the local row never lacks members.
        val memberIds = created.member_ids.ifEmpty { memberAccountIds }

        db.kotoDbQueries.upsertConversation(
            id                   = created.conversation_id,
            type                 = ConversationType.Group.wire.toLong(),
            display_name         = displayName,
            // For groups there's no single peer; reuse the first member as a
            // stable accent-color anchor and so existing code that keys avatars
            // off peer_account_id still works.
            peer_account_id      = memberIds.first(),
            avatar_url           = null,
            last_message_preview = "",
            last_message_time    = 0,
            unread_count         = 0,
            is_online            = 0,
            is_pinned            = 0,
            is_muted             = 0,
            is_archived          = 0,
            is_verified          = 0,
            member_ids_csv       = memberIds.joinToString(","),
        )
        Conversation(
            id              = created.conversation_id,
            type            = ConversationType.Group,
            displayName     = displayName,
            peerAccountId   = memberIds.first(),
            avatarUrl       = null,
            lastMessage     = "",
            lastMessageTime = 0,
            unreadCount     = 0,
            isOnline        = false,
            isPinned        = false,
            isMuted         = false,
            isVerified      = false,
            memberIds       = memberIds,
        )
    }.onFailure { log.warn("createGroup failed members={}", memberAccountIds.size, it) }

    override suspend fun setPinned(id: String, pinned: Boolean) {
        db.kotoDbQueries.setPinned(if (pinned) 1 else 0, id)
    }

    override suspend fun setMuted(id: String, muted: Boolean) {
        db.kotoDbQueries.setMuted(if (muted) 1 else 0, id)
    }

    override suspend fun setArchived(id: String, archived: Boolean) {
        db.kotoDbQueries.setArchived(if (archived) 1 else 0, id)
    }

    override suspend fun setVerified(id: String, verified: Boolean) {
        db.kotoDbQueries.setVerified(if (verified) 1 else 0, id)
    }

    /**
     * Best-effort preview from raw ciphertext length — the server never sends plaintext
     * so the actual preview string is computed when the client eventually decrypts via
     * the history sync path.
     */
    private fun base64PreviewLen(b64: String): String =
        runCatching { "[${Base64.getDecoder().decode(b64).size} bytes]" }.getOrElse { "" }

    companion object {
        private const val DEFAULT_PEER_NAME = "Koto User"
    }
}

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
