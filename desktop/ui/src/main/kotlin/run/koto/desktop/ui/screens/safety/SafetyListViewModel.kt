package run.koto.desktop.ui.screens.safety

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import run.koto.desktop.domain.model.Conversation
import run.koto.desktop.domain.repository.ConversationRepository

/**
 * Drives the "Проверка безопасности" list — every existing conversation rendered
 * as a row, split into two groups (Не проверено / Проверено). State comes
 * straight from [ConversationRepository.observeAll]; no local cache.
 */
class SafetyListViewModel(
    convRepo: ConversationRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val conversations: StateFlow<List<Conversation>> = convRepo
        .observeAll()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun close() = scope.cancel()
}
