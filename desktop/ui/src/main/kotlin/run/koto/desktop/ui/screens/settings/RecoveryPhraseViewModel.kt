package run.koto.desktop.ui.screens.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import run.koto.desktop.domain.repository.SeedRepository

/**
 * Backs the "Recovery phrase" sub-screen. Loads the persisted phrase only
 * when the user has explicitly tapped "Show" — keeps the words off-screen
 * (and out of the screenshot cache, where applicable) until requested.
 *
 * The phrase is stored encrypted via [SeedRepository]; if the row is gone
 * or fails to decrypt the screen falls back to a "phrase unavailable"
 * state and we surface a path to re-register.
 */
class RecoveryPhraseViewModel(
    private val seeds: SeedRepository,
) {
    private val log    = LoggerFactory.getLogger(RecoveryPhraseViewModel::class.java)
    private val scope  = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    sealed interface State {
        data object Hidden                        : State
        data object Loading                       : State
        data class  Revealed(val words: List<String>) : State
        data object Unavailable                   : State
    }

    private val _state = MutableStateFlow<State>(State.Hidden)
    val state: StateFlow<State> = _state.asStateFlow()

    fun reveal() {
        if (_state.value !is State.Hidden) return
        _state.value = State.Loading
        scope.launch {
            val words = runCatching { seeds.read() }.getOrNull()
            _state.value = if (words.isNullOrEmpty()) State.Unavailable
                           else State.Revealed(words)
        }
    }

    fun hide() { _state.value = State.Hidden }

    fun close() = scope.cancel()
}
