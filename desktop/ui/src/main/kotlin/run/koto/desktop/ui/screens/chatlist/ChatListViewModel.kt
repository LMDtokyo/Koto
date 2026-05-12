package run.koto.desktop.ui.screens.chatlist

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import run.koto.desktop.domain.model.Conversation
import run.koto.desktop.domain.model.ConversationType
import run.koto.desktop.domain.model.Folder
import run.koto.desktop.domain.repository.ConversationRepository
import run.koto.desktop.domain.repository.FolderRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Hosts the chat-list UI state. Wraps [ConversationRepository] — the screen
 * only sees a frozen [List]<[ChatListEntry]>, never the repo directly.
 *
 * Pin / mute / archive actions delegate to the repo; the screen reflects the
 * change automatically because the underlying Flow is reactive.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ChatListViewModel(
    private val repo       : ConversationRepository,
    private val folderRepo : FolderRepository,
) {
    private val log    = LoggerFactory.getLogger(ChatListViewModel::class.java)
    private val scope  = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Active folder filter. -1L = "All chats" (the implicit default). */
    private val _activeFolderId = MutableStateFlow(ALL_CHATS_ID)
    val activeFolderId: StateFlow<Long> = _activeFolderId.asStateFlow()

    val folders: StateFlow<List<Folder>> = folderRepo.observeFolders()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val entries: StateFlow<List<ChatListEntry>> = _activeFolderId
        .flatMapLatest { id ->
            if (id == ALL_CHATS_ID) repo.observeAll()
            else folderRepo.observeConversations(id)
        }
        .map { list -> list.map { it.toEntry() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val archived: StateFlow<List<ChatListEntry>> = repo.observeArchived()
        .map { list -> list.map { it.toEntry() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun selectFolder(id: Long) { _activeFolderId.value = id }
    fun createFolder(name: String) = scope.launch { folderRepo.create(name) }
    fun deleteFolder(id: Long)     = scope.launch {
        if (_activeFolderId.value == id) _activeFolderId.value = ALL_CHATS_ID
        folderRepo.delete(id)
    }
    fun addToFolder(folderId: Long, convId: String) = scope.launch {
        folderRepo.addConversation(folderId, convId)
    }
    fun removeFromFolder(folderId: Long, convId: String) = scope.launch {
        folderRepo.removeConversation(folderId, convId)
    }

    companion object {
        const val ALL_CHATS_ID: Long = -1L
    }

    init { sync() }

    fun sync() {
        scope.launch {
            repo.sync().onFailure { log.warn("conversation sync failed", it) }
        }
    }

    fun setPinned   (id: String, pinned: Boolean)   = scope.launch { repo.setPinned  (id, pinned)   }
    fun setMuted    (id: String, muted: Boolean)    = scope.launch { repo.setMuted   (id, muted )   }
    fun archive     (id: String)                    = scope.launch { repo.setArchived(id, true)     }
    fun setArchived (id: String, archived: Boolean) = scope.launch { repo.setArchived(id, archived) }

    fun close() = scope.cancel()
}

/**
 * UI-shaped projection of a [Conversation]. Carries everything the chat-list
 * row needs without forcing the screen to know about domain types.
 *
 * Avatar colour and initials are deterministic from [peerAccountId] so two
 * sessions of the same account render the same colour every time.
 */
data class ChatListEntry(
    val id              : String,
    val peerAccountId   : String,
    val displayName     : String,
    val initials        : String,
    val avatarColor     : Color,
    val timeLabel       : String,
    val preview         : String,
    val unreadCount     : Int,
    val pinned          : Boolean,
    val muted           : Boolean,
    val isOnline        : Boolean,
    val isGroup         : Boolean,
    val memberCount     : Int,
)

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
private val dateFmt = DateTimeFormatter.ofPattern("d MMM")

private fun Conversation.toEntry(): ChatListEntry {
    val name = displayName.ifBlank { abbreviateAccountId(peerAccountId) }
    val isGroup = type == ConversationType.Group
    return ChatListEntry(
        id              = id,
        peerAccountId   = peerAccountId,
        displayName     = name,
        initials        = if (isGroup) groupInitial(name) else initialsOf(name),
        // Group avatars seed from the conv id so they don't visually clash
        // with one of the members' direct chats.
        avatarColor     = colorFor(if (isGroup) id else peerAccountId),
        timeLabel       = formatTime(lastMessageTime),
        preview         = lastMessage,
        unreadCount     = unreadCount,
        pinned          = isPinned,
        muted           = isMuted,
        // Online dots only make sense for 1:1 chats — a group is never one
        // person's presence.
        isOnline        = !isGroup && isOnline,
        isGroup         = isGroup,
        memberCount     = memberIds.size,
    )
}

/** Single uppercase glyph for a group avatar — falls back to "#" so we
 *  never render a blank circle for a group called e.g. "🐱". */
private fun groupInitial(name: String): String {
    val first = name.firstOrNull { !it.isWhitespace() } ?: return "#"
    return first.uppercaseChar().toString()
}

private fun abbreviateAccountId(id: String): String =
    if (id.length > 10) id.take(6) + "…" + id.takeLast(4) else id

private fun initialsOf(name: String): String =
    name.split(' ', limit = 3)
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifBlank { "?" }

/** Eight-bucket palette indexed by a stable hash of the account id. */
private val palette = listOf(
    Color(0xFFFF6B35), Color(0xFF7C5CFF), Color(0xFF00A676), Color(0xFFF0B400),
    Color(0xFFE74C6F), Color(0xFF3276FF), Color(0xFF13B3A5), Color(0xFFFF7A9A),
)

private fun colorFor(accountId: String): Color {
    if (accountId.isEmpty()) return palette[0]
    var h = 0
    for (c in accountId) h = (h * 31 + c.code) and 0x7fffffff
    return palette[h % palette.size]
}

private fun formatTime(epochMs: Long): String {
    if (epochMs <= 0) return ""
    val zone   = ZoneId.systemDefault()
    val msgDay = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
    val today  = LocalDate.now(zone)
    return when {
        msgDay == today                       -> Instant.ofEpochMilli(epochMs).atZone(zone).format(timeFmt)
        msgDay == today.minusDays(1)          -> "вчера"
        msgDay.isAfter(today.minusDays(7))    -> dayOfWeek(msgDay)
        else                                  -> Instant.ofEpochMilli(epochMs).atZone(zone).format(dateFmt)
    }
}

private fun dayOfWeek(d: LocalDate): String = when (d.dayOfWeek.value) {
    1 -> "пн"; 2 -> "вт"; 3 -> "ср"; 4 -> "чт"; 5 -> "пт"; 6 -> "сб"; else -> "вс"
}
