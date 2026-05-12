package run.koto.desktop.ui.screens.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import run.koto.desktop.domain.model.AppPreferences
import run.koto.desktop.domain.model.PrivacyPreset
import run.koto.desktop.domain.repository.PreferencesRepository

/**
 * Drives the Privacy sub-screen. Reflects [PreferencesRepository] state and
 * forwards every toggle / preset change to it. The repository is the source
 * of truth — there is no intermediate UI cache, so a change made elsewhere
 * (e.g. a future "reset to defaults" action) flows through automatically.
 */
class PrivacyViewModel(
    private val prefs: PreferencesRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val state: StateFlow<AppPreferences> = prefs.observe()
        .stateIn(scope, SharingStarted.Eagerly, AppPreferences())

    fun pickPreset(preset: PrivacyPreset)         = scope.launch { prefs.setPreset(preset) }
    fun toggleReadReceipts(value: Boolean)        = scope.launch { prefs.setSendReadReceipts(value) }
    fun toggleTyping       (value: Boolean)       = scope.launch { prefs.setSendTyping(value) }
    fun toggleOnlineStatus (value: Boolean)       = scope.launch { prefs.setShowOnlineStatus(value) }
    fun toggleNotifPreview (value: Boolean)       = scope.launch { prefs.setNotifPreview(value) }
    fun toggleDiscoverable (value: Boolean)       = scope.launch { prefs.setDiscoverable(value) }

    fun close() = scope.cancel()
}
