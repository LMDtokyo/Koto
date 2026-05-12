package run.koto.desktop.ui.screens.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import run.koto.desktop.domain.model.LinkedDevice
import run.koto.desktop.domain.repository.AuthRepository

/**
 * Drives the linked-devices sub-screen. Loads the active sessions for the
 * current user, marks "this device" by comparing each row's id against the
 * client-side stored [Session.sessionId], and exposes per-row revoke +
 * "revoke all others" actions.
 */
class DevicesViewModel(
    private val auth: AuthRepository,
) {
    private val log    = LoggerFactory.getLogger(DevicesViewModel::class.java)
    private val scope  = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    sealed interface Status {
        data object Loading                  : Status
        data class  Loaded(
            val devices       : List<LinkedDevice>,
            val currentId     : String?,
        ) : Status
        data class  Failed(val message: String) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Loading)
    val status: StateFlow<Status> = _status.asStateFlow()

    /** Set of session ids currently being revoked, for per-row spinner state. */
    private val _revoking = MutableStateFlow<Set<String>>(emptySet())
    val revoking: StateFlow<Set<String>> = _revoking.asStateFlow()

    private var loadJob: Job? = null

    fun load() {
        loadJob?.cancel()
        loadJob = scope.launch {
            _status.value = Status.Loading
            // Treat blank session_id (legacy clients pre-heal-refresh) as unknown
            // so the UI guards against revoking a row we can't identify.
            val currentId = runCatching { auth.session.first()?.sessionId?.takeIf { it.isNotBlank() } }
                .getOrNull()
            auth.listDevices().fold(
                onSuccess = { _status.value = Status.Loaded(it, currentId) },
                onFailure = {
                    log.warn("listDevices failed", it)
                    _status.value = Status.Failed(it.message ?: "не удалось загрузить устройства")
                },
            )
        }
    }

    fun revoke(sessionId: String) {
        if (sessionId in _revoking.value) return
        // Defensive: never let the user revoke the device they are sitting on.
        // The UI hides the button on the current row already, but if the
        // current id couldn't be resolved (legacy state) we still refuse here.
        val s = _status.value
        if (s is Status.Loaded && (s.currentId == null || sessionId == s.currentId)) {
            log.info("refusing revoke of current/unresolved session id={}", sessionId)
            return
        }
        _revoking.value = _revoking.value + sessionId
        scope.launch {
            auth.revokeDevice(sessionId).onFailure {
                log.warn("revoke device failed id={}", sessionId, it)
            }
            _revoking.value = _revoking.value - sessionId
            load()
        }
    }

    fun revokeAllOthers() {
        val s = _status.value
        if (s !is Status.Loaded || s.currentId == null) return
        val others = s.devices.filter { it.id != s.currentId }
        if (others.isEmpty()) return
        _revoking.value = _revoking.value + others.map { it.id }
        scope.launch {
            others.forEach { auth.revokeDevice(it.id) }
            _revoking.value = _revoking.value - others.map { it.id }.toSet()
            load()
        }
    }

    fun close() {
        loadJob?.cancel()
        scope.cancel()
    }
}
