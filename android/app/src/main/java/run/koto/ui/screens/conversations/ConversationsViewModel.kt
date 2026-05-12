package run.koto.ui.screens.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import run.koto.data.prefs.AccountPrefs
import run.koto.data.repository.ChatRepository
import run.koto.data.repository.MediaRepository
import run.koto.domain.model.ConversationListState
import javax.inject.Inject

@HiltViewModel
class ConversationsViewModel @Inject constructor(
    private val chatRepository : ChatRepository,
    private val accountPrefs   : AccountPrefs,
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    /** Current user's small header profile — account id, display name initial, avatar URL. */
    data class HeaderProfile(
        val accountId : String  = "",
        val initial   : String  = "",
        val avatarUrl : String  = "",
    )

    private val _headerProfile = MutableStateFlow(HeaderProfile())
    val headerProfile: StateFlow<HeaderProfile> = _headerProfile.asStateFlow()

    private val _state = MutableStateFlow(ConversationListState())
    val state: StateFlow<ConversationListState> = _state.asStateFlow()

    private val _showNewChatSheet = MutableStateFlow(false)
    val showNewChatSheet: StateFlow<Boolean> = _showNewChatSheet.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        viewModelScope.launch {
            chatRepository.getConversationsFlow()
                .catch { _state.update { it.copy(isLoading = false) } }
                .collect { convs ->
                    val immutable = convs.toImmutableList()
                    _state.update {
                        it.copy(
                            conversations = immutable.filter { c -> !c.isPinned }.toImmutableList(),
                            pinnedConvs   = immutable.filter { c -> c.isPinned }.toImmutableList(),
                            isLoading     = false,
                        )
                    }
                }
        }

        viewModelScope.launch {
            val accountId   = accountPrefs.getAccountId().orEmpty()
            val displayName = accountPrefs.getDisplayName().orEmpty()
            val avatarId    = accountPrefs.getAvatarFileId().orEmpty()
            val avatarUrl   = if (avatarId.isNotBlank()) {
                mediaRepository.getDownloadUrl(avatarId).getOrDefault("")
            } else ""
            val initial = displayName.trim().firstOrNull()?.uppercaseChar()?.toString().orEmpty()
            _headerProfile.value = HeaderProfile(
                accountId = accountId,
                initial   = initial,
                avatarUrl = avatarUrl,
            )
        }
    }

    fun onSearchQueryChange(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun showNewChat() { _showNewChatSheet.value = true }
    fun hideNewChat() { _showNewChatSheet.value = false }

    fun startNewChat(accountId: String, onOpened: (String) -> Unit) {
        viewModelScope.launch {
            chatRepository.createDirectConversation(accountId)
                .onSuccess { convId ->
                    _showNewChatSheet.value = false
                    onOpened(convId)
                }
        }
    }

    // ── Pull-to-refresh (CL-05) ────────────────────────────────────────────────

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                // chatRepository.refreshConversations() — not yet in repo; simulate network call
                delay(1000)
            } catch (e: Exception) {
                // Swallow — conversations already loaded from DB flow
            } finally {
                delay(800)  // minimum visible refresh time for UX
                _isRefreshing.value = false
            }
        }
    }

    // ── Swipe actions (CL-02) — stubs until repository layer is wired ──────────

    fun onArchive(convId: String) {
        viewModelScope.launch {
            // chatRepository.archiveConversation(convId) — not yet in repo
            android.util.Log.d("ConversationsVM", "Archive: $convId")
        }
    }

    fun onPin(convId: String) {
        viewModelScope.launch {
            // chatRepository.pinConversation(convId) — not yet in repo
            android.util.Log.d("ConversationsVM", "Pin: $convId")
        }
    }

    fun onMute(convId: String) {
        viewModelScope.launch {
            // chatRepository.muteConversation(convId) — not yet in repo
            android.util.Log.d("ConversationsVM", "Mute: $convId")
        }
    }
}
