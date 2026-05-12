package run.koto.network

import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import run.koto.data.prefs.SettingsPrefs
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages user-configured proxy (SOCKS5 / Shadowsocks / VMess / Trojan).
 *
 * Observes [SettingsPrefs] for changes and exposes the currently-active
 * [ProxyConfig] as a [StateFlow]. Consumed by [TorAwareCallFactory] to
 * route HTTP+WS traffic through the chosen proxy.
 *
 * Priority chain (implemented in [TorAwareCallFactory]):
 *   1. Tor (if CONNECTED)
 *   2. User proxy (if enabled and configured)
 *   3. Direct
 */
@Singleton
class ProxyManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsPrefs: SettingsPrefs,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _activeProxy = MutableStateFlow<ProxyConfig?>(null)
    val activeProxy: StateFlow<ProxyConfig?> = _activeProxy

    init {
        // Observe settings for proxy URI / enabled changes
        settingsPrefs.settingsFlow()
            .onEach { settings ->
                _activeProxy.value = if (settings.proxyEnabled && settings.proxyUri.isNotBlank()) {
                    ProxyUri.parse(settings.proxyUri)
                } else {
                    null
                }
            }
            .launchIn(scope)
    }

    /** Returns true if the URI can be parsed into a valid proxy config. */
    fun validate(uri: String): ProxyConfig? = ProxyUri.parse(uri)
}
