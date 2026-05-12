package run.koto.desktop.ui.screens.others

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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import run.koto.desktop.domain.DomainError
import run.koto.desktop.domain.repository.ConversationRepository
import run.koto.desktop.domain.repository.ProfileRepository

/**
 * Drives the New Chat picker. Two resolution paths:
 *   1. **Koto ID** — paste 64 hex chars (or 66 with the legacy `0x05` prefix);
 *      the screen lets you create a chat without any server lookup.
 *   2. **@username** — debounced lookup against `/v1/users/by-username/{name}`.
 *
 * On confirm the screen calls [createChatWith] which returns the new
 * conversation id; the screen navigates to that id in the host nav stack.
 */
@OptIn(FlowPreview::class)
class NewChatViewModel(
    private val profile : ProfileRepository,
    private val convRepo : ConversationRepository,
) {
    private val log    = LoggerFactory.getLogger(NewChatViewModel::class.java)
    private val scope  = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    sealed interface Resolution {
        data object Idle                                         : Resolution
        data class  KotoId       (val accountId: String)         : Resolution
        data object UsernameSeed                                 : Resolution  // user is typing a partial @handle
        data object Resolving                                    : Resolution
        data class  Resolved     (val accountId: String, val displayName: String, val username: String) : Resolution
        data object NotFound                                     : Resolution
        data class  Invalid      (val reason: String)            : Resolution
    }

    data class UiState(
        val query        : String     = "",
        val resolution   : Resolution = Resolution.Idle,
        val creating     : Boolean    = false,
        val createError  : String?    = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val queryFlow  = MutableStateFlow("")

    init {
        // Debounced username lookups — only for inputs that look like an @handle.
        queryFlow
            .drop(1)
            .debounce(300)
            .distinctUntilChanged()
            .onEach(::resolve)
            .launchIn(scope)
    }

    fun onQueryChange(value: String) {
        _state.value = _state.value.copy(query = value, createError = null)
        queryFlow.value = value

        // Apply quick synchronous classification right away so the UI flips
        // to the right card without waiting for the debounce. Username
        // resolution still waits the 300 ms.
        val classification = classify(value)
        if (classification !is Resolution.UsernameSeed) {
            _state.value = _state.value.copy(resolution = classification)
        } else {
            _state.value = _state.value.copy(resolution = Resolution.UsernameSeed)
        }
    }

    /**
     * Create or open a direct conversation with [accountId]. Returns the
     * conversation id on success so the caller can navigate. Idempotent —
     * the server returns the existing conversation if one already exists.
     */
    fun createChatWith(accountId: String, onOpened: (String) -> Unit) {
        if (_state.value.creating) return
        _state.value = _state.value.copy(creating = true, createError = null)
        scope.launch {
            convRepo.createDirect(accountId).fold(
                onSuccess = { conv ->
                    _state.value = _state.value.copy(creating = false, createError = null)
                    onOpened(conv.id)
                },
                onFailure = { e ->
                    log.warn("createDirect failed", e)
                    _state.value = _state.value.copy(
                        creating    = false,
                        createError = e.message ?: "не удалось создать чат",
                    )
                },
            )
        }
    }

    fun close() = scope.cancel()

    // ── Internals ─────────────────────────────────────────────────────────

    private suspend fun resolve(query: String) {
        val sync = classify(query)
        if (sync !is Resolution.UsernameSeed) return  // already final
        // Username lookup
        _state.value = _state.value.copy(resolution = Resolution.Resolving)
        val handle = query.trimStart('@').trim()
        profile.findByUsername(handle).fold(
            onSuccess = { p ->
                if (_state.value.query == query) {
                    val resolvedHandle = p.username?.takeIf { it.isNotBlank() } ?: handle
                    _state.value = _state.value.copy(
                        resolution = Resolution.Resolved(
                            accountId   = p.accountId,
                            displayName = p.displayName.ifBlank { "@$resolvedHandle" },
                            username    = resolvedHandle,
                        ),
                    )
                }
            },
            onFailure = { e ->
                if (_state.value.query == query) {
                    val res = if (e is DomainError.NotFound) Resolution.NotFound
                              else Resolution.Invalid(e.message ?: "ошибка поиска")
                    _state.value = _state.value.copy(resolution = res)
                }
            },
        )
    }

    /** Synchronous shape classifier — purely string regex, no I/O. */
    private fun classify(raw: String): Resolution {
        val t = raw.trim()
        if (t.isEmpty()) return Resolution.Idle

        // Koto ID — 64 hex (raw account_id) or 66 hex starting with "05" (legacy
        // libsignal serialised public-key form). Either way we want the 64-char body.
        val hex64 = Regex("^[0-9a-f]{64}$", RegexOption.IGNORE_CASE)
        val hex66 = Regex("^05[0-9a-f]{64}$", RegexOption.IGNORE_CASE)
        if (hex64.matches(t))           return Resolution.KotoId(t.lowercase())
        if (hex66.matches(t))           return Resolution.KotoId(t.substring(2).lowercase())

        // Partial hex — give the user feedback that they're typing a Koto ID.
        if (t.matches(Regex("^0?5?[0-9a-f]+$", RegexOption.IGNORE_CASE)) && t.length >= 4 && t.length < 66)
            return Resolution.Invalid("введите все ${if (t.startsWith("05", true)) 66 else 64} символа Koto ID")

        // Username path: starts with @ (or a letter, since users may omit the @).
        val handle = t.trimStart('@')
        if (handle.length in 5..32 && handle.matches(Regex("^[a-z][a-z0-9_]+$"))) {
            return Resolution.UsernameSeed
        }
        if (handle.startsWith("@") || handle.length in 1..4) {
            return Resolution.Invalid("минимум 5 символов имени")
        }
        return Resolution.Invalid("неизвестный формат — введите Koto ID или @username")
    }
}
