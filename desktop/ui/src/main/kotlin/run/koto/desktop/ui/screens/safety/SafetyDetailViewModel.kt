package run.koto.desktop.ui.screens.safety

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import run.koto.desktop.domain.repository.AuthRepository
import run.koto.desktop.domain.repository.ConversationRepository
import run.koto.desktop.domain.util.SafetyFingerprint

/**
 * Drives the per-contact safety screen — derives the 60-digit fingerprint from
 * the local + peer account ids, exposes the verified flag, and writes back to
 * the conversation row when the user toggles it.
 */
class SafetyDetailViewModel(
    private val convRepo : ConversationRepository,
    private val auth     : AuthRepository,
) {
    private val log   = LoggerFactory.getLogger(SafetyDetailViewModel::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    data class State(
        val loading         : Boolean = true,
        val conversationId  : String  = "",
        val displayName     : String  = "",
        val peerAccountId   : String  = "",
        val selfAccountId   : String  = "",
        val safetyNumber    : String  = "",
        val safetyShareText : String  = "",
        val isVerified      : Boolean = false,
        val isGroup         : Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var loadJob: Job? = null

    fun load(convId: String) {
        loadJob?.cancel()
        loadJob = scope.launch {
            val selfId = auth.currentAccountId().orEmpty()
            // Resolve the conversation row from the live observe-all stream — same
            // source the list screen reads, so the toggle state stays consistent.
            val conv = convRepo.observeAll().first().firstOrNull { it.id == convId }
                ?: convRepo.observeArchived().first().firstOrNull { it.id == convId }

            if (conv == null) {
                log.warn("safety detail: conversation {} not found", convId)
                _state.value = State(loading = false, conversationId = convId)
                return@launch
            }

            // For groups the "peer_account_id" is just the first member used as an
            // accent anchor — fingerprint comparison doesn't apply, so we keep the
            // toggle off-screen by flagging isGroup.
            val isGroup = false // current desktop schema treats every row uniformly
            val fingerprint = if (selfId.isNotBlank() && conv.peerAccountId.isNotBlank()) {
                SafetyFingerprint.forPair(selfId, conv.peerAccountId)
            } else {
                ""
            }
            // Sharable text — the same payload encoded in the QR. Encoding both
            // ids lets the other side (web/mobile) re-derive the same number
            // independent of platform.
            val share = "koto://safety?self=$selfId&peer=${conv.peerAccountId}"

            _state.value = State(
                loading         = false,
                conversationId  = conv.id,
                displayName     = conv.displayName,
                peerAccountId   = conv.peerAccountId,
                selfAccountId   = selfId,
                safetyNumber    = fingerprint,
                safetyShareText = share,
                isVerified      = conv.isVerified,
                isGroup         = isGroup,
            )
        }
    }

    fun setVerified(verified: Boolean) {
        val cur = _state.value
        if (cur.conversationId.isBlank()) return
        _state.value = cur.copy(isVerified = verified)
        scope.launch {
            runCatching { convRepo.setVerified(cur.conversationId, verified) }
                .onFailure {
                    log.warn("setVerified failed conv={} verified={}", cur.conversationId, verified, it)
                    // Roll back the optimistic flip if the write failed.
                    _state.value = _state.value.copy(isVerified = cur.isVerified)
                }
        }
    }

    fun close() {
        loadJob?.cancel()
        scope.cancel()
    }
}
