package run.koto.security

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import run.koto.data.prefs.SettingsPrefs
import javax.inject.Inject
import javax.inject.Singleton

private const val GRACE_MS = 3_000L   // 3 s before lock engages after background

@Singleton
class AppLockManager @Inject constructor(
    private val settingsPrefs: SettingsPrefs,
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private var lockJob: Job? = null

    /** Called once from KotoApp.onCreate — registers lifecycle observer on main thread. */
    fun init() {
        // When screen lock is disabled, immediately unlock regardless of state.
        scope.launch {
            settingsPrefs.settingsFlow()
                .map { it.screenLock }
                .distinctUntilChanged()
                .collect { enabled ->
                    if (!enabled) _isLocked.value = false
                }
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            // App is going to the background (all activities stopped).
            override fun onStop(owner: LifecycleOwner) {
                scope.launch {
                    val enabled = settingsPrefs.settingsFlow().first().screenLock
                    if (enabled) {
                        lockJob?.cancel()
                        lockJob = scope.launch {
                            delay(GRACE_MS)
                            _isLocked.value = true
                        }
                    }
                }
            }

            // App is returning to the foreground.
            override fun onStart(owner: LifecycleOwner) {
                // Cancel grace-period timer so a quick return doesn't trigger lock.
                lockJob?.cancel()
            }
        })
    }

    fun unlock() {
        _isLocked.value = false
    }
}
