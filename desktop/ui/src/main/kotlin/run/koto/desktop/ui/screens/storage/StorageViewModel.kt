package run.koto.desktop.ui.screens.storage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import run.koto.desktop.domain.model.StorageInfo
import run.koto.desktop.domain.repository.StorageRepository

/**
 * Drives the Storage sub-screen. Loads the snapshot once, exposes per-action
 * "busy" state so the buttons can render a spinner without locking the whole
 * UI, and reloads after every successful clear.
 */
class StorageViewModel(
    private val repo: StorageRepository,
) {
    private val log   = LoggerFactory.getLogger(StorageViewModel::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    sealed interface BusyKind {
        data object Loading            : BusyKind
        data object ClearingCache      : BusyKind
        data object ClearingMessages   : BusyKind
        data class  ClearingConv(val convId: String) : BusyKind
    }

    private val _info = MutableStateFlow<StorageInfo?>(null)
    val info: StateFlow<StorageInfo?> = _info.asStateFlow()

    private val _busy = MutableStateFlow<BusyKind?>(null)
    val busy: StateFlow<BusyKind?> = _busy.asStateFlow()

    private var loadJob: Job? = null

    fun load() {
        loadJob?.cancel()
        loadJob = scope.launch {
            _busy.value = BusyKind.Loading
            runCatching { repo.snapshot() }
                .onSuccess { _info.value = it }
                .onFailure { log.warn("storage snapshot failed", it) }
            _busy.value = null
        }
    }

    fun clearMediaCache() {
        if (_busy.value != null) return
        scope.launch {
            _busy.value = BusyKind.ClearingCache
            runCatching { repo.clearMediaCache() }.onFailure { log.warn("clearMediaCache failed", it) }
            _busy.value = null
            load()
        }
    }

    fun clearAllMessages() {
        if (_busy.value != null) return
        scope.launch {
            _busy.value = BusyKind.ClearingMessages
            runCatching { repo.clearAllMessages() }.onFailure { log.warn("clearAllMessages failed", it) }
            _busy.value = null
            load()
        }
    }

    fun clearConversation(conversationId: String) {
        if (_busy.value != null) return
        scope.launch {
            _busy.value = BusyKind.ClearingConv(conversationId)
            runCatching { repo.clearConversationMessages(conversationId) }
                .onFailure { log.warn("clearConversationMessages failed conv={}", conversationId, it) }
            _busy.value = null
            load()
        }
    }

    fun close() {
        loadJob?.cancel()
        scope.cancel()
    }
}
