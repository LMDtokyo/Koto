package run.koto.desktop.ui.screens.conversation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import run.koto.desktop.domain.model.Conversation
import run.koto.desktop.domain.model.Message
import run.koto.desktop.domain.repository.ChatRepository
import run.koto.desktop.domain.repository.ConversationRepository

/**
 * Drives [ConversationScreen]. Holds the conversation id, exposes the live
 * message Flow, and turns user actions (send / markRead / delete) into
 * repository calls.
 *
 * The screen is reused across conversations — call [open] when the user
 * navigates into a new chat. The view-model is a Koin singleton (one open
 * chat at a time on desktop) so its scope outlives composition.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ConversationViewModel(
    private val chatRepo : ChatRepository,
    private val convRepo : ConversationRepository,
) {
    private val log    = LoggerFactory.getLogger(ConversationViewModel::class.java)
    private val scope  = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _conversationId = MutableStateFlow<String?>(null)
    val conversationId: StateFlow<String?> = _conversationId.asStateFlow()

    /** Conversation metadata (peer name, avatar, online flag) for the header. */
    val current: StateFlow<Conversation?> = _conversationId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else convRepo.observeAll().let { all ->
                kotlinx.coroutines.flow.combine(all, flowOf(id)) { list, _ ->
                    list.firstOrNull { it.id == id }
                }
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val messages: StateFlow<List<Message>> = _conversationId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else chatRepo.observeMessages(id)
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _sendError = MutableStateFlow<String?>(null)
    val sendError: StateFlow<String?> = _sendError.asStateFlow()

    /** Begin observing [conversationId]. Triggers a one-shot history sync. */
    fun open(conversationId: String) {
        if (_conversationId.value == conversationId) return
        _conversationId.value = conversationId
        _sendError.value = null
        scope.launch {
            chatRepo.syncMessages(conversationId).onFailure { log.warn("sync messages failed", it) }
        }
    }

    fun send(text: String, replyToId: String? = null) {
        val id = _conversationId.value ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        scope.launch {
            chatRepo.send(id, trimmed, replyToId).onFailure {
                log.warn("send failed", it)
                _sendError.value = it.message ?: "не удалось отправить"
            }
        }
    }

    fun markRead(messageId: String) {
        val id = _conversationId.value ?: return
        scope.launch { chatRepo.markRead(id, messageId) }
    }

    fun delete(messageId: String) {
        val id = _conversationId.value ?: return
        scope.launch { chatRepo.delete(id, messageId).onFailure { log.warn("delete failed", it) } }
    }

    fun editMessage(messageId: String, newPlaintext: String) {
        val id = _conversationId.value ?: return
        val trimmed = newPlaintext.trim()
        if (trimmed.isEmpty()) return
        scope.launch {
            chatRepo.editMessage(id, messageId, trimmed).onFailure {
                log.warn("edit failed msg={}", messageId, it)
                _sendError.value = it.message ?: "не удалось изменить"
            }
        }
    }

    fun forward(messageId: String, destinationConversationIds: List<String>) {
        val id = _conversationId.value ?: return
        if (destinationConversationIds.isEmpty()) return
        scope.launch {
            chatRepo.forward(id, messageId, destinationConversationIds).onFailure {
                log.warn("forward failed msg={}", messageId, it)
                _sendError.value = it.message ?: "не удалось переслать"
            }
        }
    }

    fun togglePin(messageId: String, currentlyPinned: Boolean) {
        val id = _conversationId.value ?: return
        scope.launch {
            chatRepo.setPinned(id, messageId, !currentlyPinned).onFailure {
                log.warn("pin toggle failed msg={}", messageId, it)
            }
        }
    }

    fun toggleReaction(messageId: String, emoji: String) {
        val id = _conversationId.value ?: return
        scope.launch {
            chatRepo.toggleReaction(id, messageId, emoji).onFailure {
                log.warn("toggleReaction failed msg={} emoji={}", messageId, emoji, it)
            }
        }
    }

    fun clearSendError() { _sendError.value = null }

    fun close() { scope.cancel() }
}
