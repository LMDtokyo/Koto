package run.koto.desktop.ui.screens.others

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
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
 * Backs [NewGroupScreen]. Holds the group name, the candidate input field
 * and the resolved member list. Same Koto-id-or-@username dispatcher as
 * [NewChatViewModel] but accumulates results into a multi-member list.
 */
@OptIn(FlowPreview::class)
class NewGroupViewModel(
    private val profile : ProfileRepository,
    private val convRepo : ConversationRepository,
) {
    private val log    = LoggerFactory.getLogger(NewGroupViewModel::class.java)
    private val scope  = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    sealed interface CandidateRes {
        data object Idle                                 : CandidateRes
        data object Resolving                            : CandidateRes
        data class  Resolved(val accountId: String, val label: String) : CandidateRes
        data object NotFound                             : CandidateRes
        data class  Invalid(val reason: String)          : CandidateRes
    }

    data class Member(val accountId: String, val label: String)

    data class UiState(
        val name                : String         = "",
        val candidate           : String         = "",
        val candidateResolution : CandidateRes   = CandidateRes.Idle,
        val members             : List<Member>   = emptyList(),
        val creating            : Boolean        = false,
        val createError         : String?        = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val candidateFlow = MutableStateFlow("")

    init {
        candidateFlow
            .drop(1)
            .debounce(300)
            .distinctUntilChanged()
            .onEach(::resolve)
            .launchIn(scope)
    }

    fun onNameChange(value: String) {
        _state.value = _state.value.copy(name = value.take(64), createError = null)
    }

    fun onCandidateChange(value: String) {
        _state.value = _state.value.copy(candidate = value, createError = null)
        candidateFlow.value = value
        // Synchronous classification — empty/short → idle so the chip is gone.
        val cls = classify(value)
        if (cls !is CandidateRes.Resolving) {
            _state.value = _state.value.copy(candidateResolution = cls)
        }
    }

    fun commitCandidate() {
        val res = _state.value.candidateResolution as? CandidateRes.Resolved ?: return
        val s = _state.value
        if (s.members.any { it.accountId == res.accountId }) {
            // Already in the list — clear the input but keep the chip.
            _state.value = s.copy(candidate = "", candidateResolution = CandidateRes.Idle)
            return
        }
        _state.value = s.copy(
            members             = s.members + Member(res.accountId, res.label),
            candidate           = "",
            candidateResolution = CandidateRes.Idle,
        )
        candidateFlow.value = ""
    }

    fun removeMember(accountId: String) {
        _state.value = _state.value.copy(
            members = _state.value.members.filterNot { it.accountId == accountId },
        )
    }

    fun create(onCreated: (String) -> Unit) {
        val s = _state.value
        if (s.creating || s.members.isEmpty()) return
        _state.value = s.copy(creating = true, createError = null)
        scope.launch {
            convRepo.createGroup(s.name.trim(), s.members.map { it.accountId }).fold(
                onSuccess = { conv ->
                    _state.value = UiState()  // reset the form for next time
                    onCreated(conv.id)
                },
                onFailure = { e ->
                    log.warn("createGroup failed", e)
                    _state.value = _state.value.copy(
                        creating    = false,
                        createError = e.message ?: "не удалось создать группу",
                    )
                },
            )
        }
    }

    fun close() = scope.cancel()

    // ── Internals ─────────────────────────────────────────────────────────

    private suspend fun resolve(query: String) {
        if (classify(query) !is CandidateRes.Resolving) return
        _state.value = _state.value.copy(candidateResolution = CandidateRes.Resolving)
        val handle = query.trimStart('@').trim()
        profile.findByUsername(handle).fold(
            onSuccess = { p ->
                if (_state.value.candidate == query) {
                    val handle = p.username?.takeIf { it.isNotBlank() } ?: handle
                    val label  = p.displayName.ifBlank { "@$handle" }
                    _state.value = _state.value.copy(
                        candidateResolution = CandidateRes.Resolved(p.accountId, label),
                    )
                }
            },
            onFailure = { e ->
                if (_state.value.candidate == query) {
                    val res = if (e is DomainError.NotFound) CandidateRes.NotFound
                              else CandidateRes.Invalid(e.message ?: "ошибка поиска")
                    _state.value = _state.value.copy(candidateResolution = res)
                }
            },
        )
    }

    private fun classify(raw: String): CandidateRes {
        val t = raw.trim()
        if (t.isEmpty()) return CandidateRes.Idle

        val hex64 = Regex("^[0-9a-f]{64}$", RegexOption.IGNORE_CASE)
        val hex66 = Regex("^05[0-9a-f]{64}$", RegexOption.IGNORE_CASE)
        if (hex64.matches(t)) return CandidateRes.Resolved(t.lowercase(), "${t.take(10)}…${t.takeLast(6)}")
        if (hex66.matches(t)) {
            val id = t.substring(2).lowercase()
            return CandidateRes.Resolved(id, "${id.take(10)}…${id.takeLast(6)}")
        }
        if (t.matches(Regex("^0?5?[0-9a-f]+$", RegexOption.IGNORE_CASE)) && t.length >= 4 && t.length < 66)
            return CandidateRes.Invalid("введите все 64 (или 66 со знаком 05) символа Koto ID")

        val handle = t.trimStart('@')
        if (handle.length in 5..32 && handle.matches(Regex("^[a-z][a-z0-9_]+$")))
            return CandidateRes.Resolving
        if (handle.length in 1..4) return CandidateRes.Invalid("минимум 5 символов имени")
        return CandidateRes.Invalid("неизвестный формат — введите Koto ID или @username")
    }
}
