package run.koto.desktop.ui.screens.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import run.koto.desktop.domain.DomainError
import run.koto.desktop.domain.model.UsernameAvailability
import run.koto.desktop.domain.repository.ProfileRepository

/**
 * Drives the username sub-screen. Holds the candidate text the user is
 * typing, runs a debounced availability probe, and exposes the result via
 * [state] so the input field can colour itself per state.
 *
 * The debounce protects the backend from a request per keystroke and is the
 * same trick Telegram's `@username` form uses (~300 ms feels instant but
 * waits for the user to stop typing).
 */
@OptIn(FlowPreview::class)
class UsernameViewModel(
    private val profile: ProfileRepository,
) {
    private val log    = LoggerFactory.getLogger(UsernameViewModel::class.java)
    private val scope  = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    sealed interface CheckState {
        data object Idle                         : CheckState
        data object Checking                     : CheckState
        data object Available                    : CheckState
        data object Taken                        : CheckState
        data class  Invalid(val reason: String)  : CheckState
    }

    sealed interface SaveState {
        data object Idle                         : SaveState
        data object Saving                       : SaveState
        data object Saved                        : SaveState
        data class  Failed(val message: String)  : SaveState
    }

    data class UiState(
        val savedUsername : String?     = null,
        val candidate     : String      = "",
        val check         : CheckState  = CheckState.Idle,
        val save          : SaveState   = SaveState.Idle,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val candidateFlow = MutableStateFlow("")
    private var loadJob: Job? = null

    init {
        candidateFlow
            .drop(1)                                  // ignore the initial empty seed
            .debounce(350)
            .distinctUntilChanged()
            .filter { it.isNotEmpty() && it != _state.value.savedUsername }
            .onEach { candidate ->
                _state.value = _state.value.copy(check = CheckState.Checking)
                runCheck(candidate)
            }
            .launchIn(scope)
    }

    /** Load the current username so the screen knows what to show as "saved". */
    fun load() {
        loadJob?.cancel()
        loadJob = scope.launch {
            profile.me().onSuccess { p ->
                _state.value = _state.value.copy(
                    savedUsername = p.username,
                    candidate     = p.username.orEmpty(),
                )
            }.onFailure { log.warn("load profile failed", it) }
        }
    }

    fun onCandidateChange(value: String) {
        // Mirror Telegram's restriction: lowercase a-z, 0-9, _ only. Anything
        // else gets stripped silently rather than thrown back as an error.
        val sanitized = value.lowercase().filter { it.isLetterOrDigit() || it == '_' }.take(32)
        _state.value = _state.value.copy(
            candidate = sanitized,
            // Reset save status as soon as the user starts editing again.
            save      = if (_state.value.save is SaveState.Saved) SaveState.Idle else _state.value.save,
            check     = if (sanitized.isEmpty() || sanitized == _state.value.savedUsername)
                            CheckState.Idle
                        else CheckState.Idle, // keep Idle until debounce fires
        )
        candidateFlow.value = sanitized
    }

    fun save() {
        val s = _state.value
        if (s.check !is CheckState.Available) return
        if (s.save is SaveState.Saving) return
        _state.value = s.copy(save = SaveState.Saving)
        scope.launch {
            profile.setUsername(s.candidate).fold(
                onSuccess = { saved ->
                    _state.value = _state.value.copy(
                        savedUsername = saved,
                        candidate     = saved,
                        save          = SaveState.Saved,
                        check         = CheckState.Idle,
                    )
                },
                onFailure = { e ->
                    val msg = when (e) {
                        is DomainError.AlreadyExists -> "@${s.candidate} занят"
                        is DomainError.InvalidInput  -> e.message ?: "некорректное имя"
                        else                          -> "не удалось сохранить"
                    }
                    _state.value = _state.value.copy(save = SaveState.Failed(msg))
                    if (e is DomainError.AlreadyExists) {
                        _state.value = _state.value.copy(check = CheckState.Taken)
                    }
                },
            )
        }
    }

    fun close() {
        loadJob?.cancel()
        scope.cancel()
    }

    private suspend fun runCheck(candidate: String) {
        profile.checkUsername(candidate).fold(
            onSuccess = { res ->
                val mapped = when (res) {
                    UsernameAvailability.Available             -> CheckState.Available
                    UsernameAvailability.Taken                 -> CheckState.Taken
                    is UsernameAvailability.Invalid            -> CheckState.Invalid(humanise(res.reason))
                }
                if (_state.value.candidate == candidate) {
                    _state.value = _state.value.copy(check = mapped)
                }
            },
            onFailure = { e ->
                log.warn("check username failed", e)
                if (_state.value.candidate == candidate) {
                    _state.value = _state.value.copy(check = CheckState.Invalid("проверка недоступна"))
                }
            },
        )
    }

    private fun humanise(serverReason: String): String = when (serverReason) {
        "username required"                                           -> "введите имя"
        "too short — min 5 characters"                                -> "минимум 5 символов"
        "too long — max 32 characters"                                -> "максимум 32 символа"
        "must start with a letter and use only a–z, 0–9, _"           -> "только латиница, цифры, _; начинать с буквы"
        "reserved"                                                     -> "это имя зарезервировано"
        else                                                           -> serverReason
    }
}
