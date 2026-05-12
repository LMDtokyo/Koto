package run.koto.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import run.koto.data.repository.ChatRepository
import run.koto.domain.model.ChatItem
import run.koto.domain.model.ChatState
import run.koto.domain.model.MessageUi
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    /** Processed list items — computed on Default dispatcher to avoid blocking composition. */
    val chatItems: StateFlow<ImmutableList<ChatItem>> = _state
        .map { s -> buildChatItems(s.messages) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, persistentListOf())

    /** Currently selected message for reply preview (CH-05). Null = no reply selected. */
    val replyTarget = MutableStateFlow<MessageUi?>(null)

    /** Count of messages below the visible fold (CH-08). Updated by ChatScreen scroll listener. */
    private val _unreadBelowFold = MutableStateFlow(0)
    val unreadBelowFold: StateFlow<Int> = _unreadBelowFold.asStateFlow()

    private var currentConvId: String = ""

    fun load(convId: String) {
        currentConvId = convId
        viewModelScope.launch {
            chatRepository.getMessagesFlow(convId)
                .collect { messages ->
                    val immutableMessages = messages.toImmutableList()
                    _state.update { it.copy(messages = immutableMessages) }
                }
        }
        viewModelScope.launch {
            chatRepository.getConversationInfo(convId)?.let { info ->
                _state.update {
                    it.copy(
                        displayName = info.displayName,
                        peerId      = info.peerId,
                        online      = info.online,
                    )
                }
            }
        }
    }

    fun onInputChange(text: String) {
        _state.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val text = _state.value.inputText.trim()
        android.util.Log.d("ChatVM", "sendMessage called: text='${text.take(20)}' convId='$currentConvId'")
        if (text.isBlank() || currentConvId.isEmpty()) {
            android.util.Log.w("ChatVM", "sendMessage aborted: blank=${text.isBlank()} emptyConv=${currentConvId.isEmpty()}")
            return
        }

        _state.update { it.copy(inputText = "", sending = true) }
        clearReply()

        viewModelScope.launch {
            val result = chatRepository.sendMessage(currentConvId, text)
            result.fold(
                onSuccess = {
                    android.util.Log.i("ChatVM", "sendMessage success")
                    _state.update { it.copy(sending = false) }
                },
                onFailure = { err ->
                    android.util.Log.e("ChatVM", "sendMessage failed: ${err.javaClass.simpleName}: ${err.message}", err)
                    _state.update { s -> s.copy(inputText = text, sending = false) }
                }
            )
        }
    }

    fun onReply(msg: MessageUi) { replyTarget.value = msg }
    fun clearReply() { replyTarget.value = null }
    fun onLongPress(msgId: String) { /* context menu handled in ChatScreen state; exposed for lambda stability */ }
    fun updateUnreadBelowFold(count: Int) { _unreadBelowFold.value = count }
}
