package run.koto.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

private val Context.settingsStore: DataStore<Preferences>
    by preferencesDataStore(name = "koto_settings")

@Singleton
class SettingsPrefs @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class Settings(
        val screenLock           : Boolean = false,
        val hideFromRecents      : Boolean = true,
        val disappearingMessages : Boolean = false,
        val relayEnabled         : Boolean = true,
        val torEnabled           : Boolean = false,
        val customRelayUrl       : String  = "",
        val notificationsEnabled : Boolean = true,
        val darkMode             : Boolean = true,
        val proxyEnabled         : Boolean = false,
        val proxyUri             : String  = "",   // ss://, vmess://, socks5://
    )

    private val KEY_SCREEN_LOCK     = booleanPreferencesKey("screen_lock")
    private val KEY_HIDE_RECENTS    = booleanPreferencesKey("hide_recents")
    private val KEY_DISAPPEARING    = booleanPreferencesKey("disappearing")
    private val KEY_RELAY           = booleanPreferencesKey("relay_enabled")
    private val KEY_TOR             = booleanPreferencesKey("tor_enabled")
    private val KEY_RELAY_URL       = stringPreferencesKey("custom_relay_url")
    private val KEY_NOTIFICATIONS   = booleanPreferencesKey("notifications")
    private val KEY_DARK_MODE       = booleanPreferencesKey("dark_mode")
    private val KEY_PROXY_ENABLED   = booleanPreferencesKey("proxy_enabled")
    private val KEY_PROXY_URI       = stringPreferencesKey("proxy_uri")

    fun settingsFlow(): Flow<Settings> =
        context.settingsStore.data.map { prefs -> prefs.toSettings() }

    private fun Preferences.toSettings() = Settings(
        screenLock           = this[KEY_SCREEN_LOCK]   ?: false,
        hideFromRecents      = this[KEY_HIDE_RECENTS]  ?: true,
        disappearingMessages = this[KEY_DISAPPEARING]  ?: false,
        relayEnabled         = this[KEY_RELAY]         ?: true,
        torEnabled           = this[KEY_TOR]           ?: false,
        customRelayUrl       = this[KEY_RELAY_URL]     ?: "",
        notificationsEnabled = this[KEY_NOTIFICATIONS] ?: true,
        darkMode             = this[KEY_DARK_MODE]     ?: true,
        proxyEnabled         = this[KEY_PROXY_ENABLED] ?: false,
        proxyUri             = this[KEY_PROXY_URI]     ?: "",
    )

    suspend fun load(): Settings {
        val prefs = context.settingsStore.data.firstOrNull() ?: return Settings()
        return prefs.toSettings()
    }

    suspend fun save(settings: Settings) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_SCREEN_LOCK]   = settings.screenLock
            prefs[KEY_HIDE_RECENTS]  = settings.hideFromRecents
            prefs[KEY_DISAPPEARING]  = settings.disappearingMessages
            prefs[KEY_RELAY]         = settings.relayEnabled
            prefs[KEY_TOR]           = settings.torEnabled
            prefs[KEY_RELAY_URL]     = settings.customRelayUrl
            prefs[KEY_NOTIFICATIONS] = settings.notificationsEnabled
            prefs[KEY_DARK_MODE]     = settings.darkMode
            prefs[KEY_PROXY_ENABLED] = settings.proxyEnabled
            prefs[KEY_PROXY_URI]     = settings.proxyUri
        }
    }

    /** Observable flow of dark mode preference for Theme composable. */
    val darkModeFlow: Flow<Boolean> = context.settingsStore.data.map { prefs ->
        prefs[KEY_DARK_MODE] ?: true
    }
}
