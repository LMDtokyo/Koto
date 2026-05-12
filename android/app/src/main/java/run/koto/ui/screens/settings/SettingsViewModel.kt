package run.koto.ui.screens.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import run.koto.data.prefs.AccountPrefs
import run.koto.data.prefs.SettingsPrefs
import run.koto.data.remote.api.UpdateProfileRequest
import run.koto.data.remote.api.UserApi
import run.koto.data.repository.MediaRepository
import run.koto.network.ProxyConfig
import run.koto.network.ProxyManager
import run.koto.network.ProxyUri
import run.koto.network.TorManager
import run.koto.network.TorStatus
import run.koto.security.SecurityManager
import javax.inject.Inject

data class SettingsState(
    val isLoaded             : Boolean    = false,  // false → don't render switches yet
    val accountId            : String     = "",
    val displayName          : String     = "",
    val screenLock           : Boolean    = false,
    val hideFromRecents      : Boolean    = false,
    val disappearingMessages : Boolean    = false,
    val relayEnabled         : Boolean    = false,
    val torEnabled           : Boolean    = false,
    val torStatus            : TorStatus  = TorStatus.DISABLED,
    val torMessage           : String?    = null,
    val customRelayUrl       : String     = "",
    val notificationsEnabled : Boolean    = false,
    val showRelayDialog      : Boolean    = false,
    // Avatar
    val avatarFileId         : String     = "",
    val avatarDisplayUrl     : String     = "",  // short-lived presigned URL for Coil
    val isUploadingAvatar    : Boolean    = false,
    val avatarError          : String?    = null,
    val darkMode             : Boolean    = true,
    // User proxy (Shadowsocks / VMess / SOCKS5 / Trojan)
    val proxyEnabled         : Boolean    = false,
    val proxyUri             : String     = "",
    val proxyConfig          : ProxyConfig? = null,  // parsed from proxyUri
    val proxyError           : String?    = null,
    val showProxyDialog      : Boolean    = false,
    // Security
    val securityStatus       : SecurityManager.SecurityStatus = SecurityManager.SecurityStatus(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val accountPrefs   : AccountPrefs,
    private val settingsPrefs  : SettingsPrefs,
    private val mediaRepository: MediaRepository,
    private val userApi        : UserApi,
    private val torManager     : TorManager,
    private val proxyManager   : ProxyManager,
    private val securityManager: SecurityManager,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch { loadState() }
        // Mirror TorManager status into UI state
        viewModelScope.launch {
            torManager.status.collect { status ->
                _state.update { it.copy(torStatus = status) }
            }
        }
        // Mirror SecurityManager status into UI state
        viewModelScope.launch {
            securityManager.status.collect { status ->
                _state.update { it.copy(securityStatus = status) }
            }
        }
    }

    private suspend fun loadState() {
        val accountId   = accountPrefs.getAccountId() ?: ""
        val displayName = accountPrefs.getDisplayName() ?: ""
        val prefs       = settingsPrefs.load()
        val avatarFileId = accountPrefs.getAvatarFileId() ?: ""

        // Fetch presigned display URL if avatar exists (expires in 1 h; refreshed each open)
        val avatarDisplayUrl = if (avatarFileId.isNotBlank()) {
            mediaRepository.getDownloadUrl(avatarFileId).getOrDefault("")
        } else ""

        _state.update {
            it.copy(
                isLoaded             = true,
                accountId            = accountId,
                displayName          = displayName,
                screenLock           = prefs.screenLock,
                hideFromRecents      = prefs.hideFromRecents,
                disappearingMessages = prefs.disappearingMessages,
                relayEnabled         = prefs.relayEnabled,
                torEnabled           = prefs.torEnabled,
                customRelayUrl       = prefs.customRelayUrl,
                notificationsEnabled = prefs.notificationsEnabled,
                darkMode             = prefs.darkMode,
                proxyEnabled         = prefs.proxyEnabled,
                proxyUri             = prefs.proxyUri,
                proxyConfig          = ProxyUri.parse(prefs.proxyUri),
                avatarFileId         = avatarFileId,
                avatarDisplayUrl     = avatarDisplayUrl,
            )
        }
    }

    // ── User proxy ────────────────────────────────────────────────────────────

    fun toggleProxy(enabled: Boolean) {
        if (enabled && _state.value.proxyConfig == null) {
            _state.update { it.copy(showProxyDialog = true, proxyError = null) }
            return
        }
        _state.update { it.copy(proxyEnabled = enabled) }
        save()
    }

    fun showProxyDialog()  { _state.update { it.copy(showProxyDialog = true, proxyError = null) } }
    fun hideProxyDialog()  { _state.update { it.copy(showProxyDialog = false, proxyError = null) } }

    /** Save a new proxy URI. Validates by parsing; returns true on success. */
    fun saveProxyUri(uri: String): Boolean {
        val trimmed = uri.trim()
        if (trimmed.isEmpty()) {
            _state.update {
                it.copy(
                    proxyUri       = "",
                    proxyConfig    = null,
                    proxyEnabled   = false,
                    showProxyDialog = false,
                    proxyError     = null,
                )
            }
            save()
            return true
        }
        val parsed = ProxyUri.parse(trimmed)
        if (parsed == null) {
            _state.update { it.copy(proxyError = "Неверный формат. Используйте ss://, vmess://, socks5:// или trojan://") }
            return false
        }
        _state.update {
            it.copy(
                proxyUri       = trimmed,
                proxyConfig    = parsed,
                proxyEnabled   = true,
                showProxyDialog = false,
                proxyError     = null,
            )
        }
        save()
        return true
    }

    fun dismissProxyError() { _state.update { it.copy(proxyError = null) } }

    // ── Avatar ────────────────────────────────────────────────────────────────

    fun uploadAvatar(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isUploadingAvatar = true, avatarError = null) }

            mediaRepository.uploadAvatar(uri)
                .onSuccess { fileId ->
                    // Update server profile
                    runCatching {
                        userApi.updateProfile(
                            UpdateProfileRequest(
                                display_name = _state.value.displayName,
                                avatar_url   = fileId,
                            )
                        )
                    }
                    // Persist locally
                    accountPrefs.saveAvatarFileId(fileId)
                    // Fetch display URL for Coil
                    val displayUrl = mediaRepository.getDownloadUrl(fileId).getOrDefault("")
                    _state.update {
                        it.copy(
                            isUploadingAvatar = false,
                            avatarFileId      = fileId,
                            avatarDisplayUrl  = displayUrl,
                        )
                    }
                }
                .onFailure { err ->
                    _state.update {
                        it.copy(
                            isUploadingAvatar = false,
                            avatarError       = err.message ?: "Upload failed",
                        )
                    }
                }
        }
    }

    /**
     * Update the user's display name. Persists locally first (optimistic),
     * then syncs with the user service so other clients can discover it via
     * GET /v1/users/{accountId}.
     */
    fun saveDisplayName(newName: String) {
        val trimmed = newName.trim()
        if (trimmed == _state.value.displayName) return
        viewModelScope.launch {
            // Optimistic: update UI + local prefs immediately
            _state.update { it.copy(displayName = trimmed) }
            accountPrefs.saveDisplayName(trimmed)

            // Sync with server — keep avatar unchanged
            runCatching {
                userApi.updateProfile(
                    UpdateProfileRequest(
                        display_name = trimmed,
                        avatar_url   = _state.value.avatarFileId,
                    )
                )
            }
        }
    }

    fun removeAvatar() {
        viewModelScope.launch {
            runCatching {
                userApi.updateProfile(
                    UpdateProfileRequest(
                        display_name = _state.value.displayName,
                        avatar_url   = "",
                    )
                )
            }
            accountPrefs.saveAvatarFileId("")
            _state.update { it.copy(avatarFileId = "", avatarDisplayUrl = "") }
        }
    }

    fun clearAvatarError() {
        _state.update { it.copy(avatarError = null) }
    }

    // ── Settings toggles ──────────────────────────────────────────────────────

    fun toggleScreenLock(enabled: Boolean) {
        _state.update { it.copy(screenLock = enabled) }
        save()
    }

    fun toggleHideFromRecents(enabled: Boolean) {
        _state.update { it.copy(hideFromRecents = enabled) }
        save()
    }

    fun toggleDisappearing(enabled: Boolean) {
        _state.update { it.copy(disappearingMessages = enabled) }
        save()
    }

    fun toggleRelay(enabled: Boolean) {
        _state.update { it.copy(relayEnabled = enabled) }
        save()
    }

    fun toggleTor(enabled: Boolean) {
        if (enabled) {
            _state.update { it.copy(torEnabled = true, torMessage = null) }
            save()
            viewModelScope.launch {
                val connected = torManager.enable()
                _state.update {
                    it.copy(
                        torStatus  = torManager.status.value,
                        torMessage = if (connected) null
                                     else "Не удалось запустить Tor — попробуйте позже",
                    )
                }
            }
            // Mirror bootstrap progress into the UI message while connecting
            viewModelScope.launch {
                torManager.bootstrapProgress.collect { pct ->
                    if (_state.value.torStatus == TorStatus.CONNECTING && pct in 1..99) {
                        _state.update {
                            it.copy(torMessage = "Подключение к Tor… $pct%")
                        }
                    }
                }
            }
        } else {
            torManager.disable()
            _state.update {
                it.copy(
                    torEnabled = false,
                    torStatus  = TorStatus.DISABLED,
                    torMessage = null,
                )
            }
            save()
        }
    }

    fun dismissTorMessage() {
        _state.update { it.copy(torMessage = null) }
    }

    fun showCustomRelay() {
        _state.update { it.copy(showRelayDialog = true) }
    }

    fun hideCustomRelay() {
        _state.update { it.copy(showRelayDialog = false) }
    }

    fun saveCustomRelayUrl(url: String) {
        _state.update { it.copy(customRelayUrl = url, showRelayDialog = false) }
        save()
    }

    fun toggleNotifications(enabled: Boolean) {
        _state.update { it.copy(notificationsEnabled = enabled) }
        save()
    }

    fun toggleDarkMode(enabled: Boolean) {
        _state.update { it.copy(darkMode = enabled) }
        save()
    }

    private fun save() {
        val s = _state.value
        viewModelScope.launch {
            settingsPrefs.save(
                SettingsPrefs.Settings(
                    screenLock           = s.screenLock,
                    hideFromRecents      = s.hideFromRecents,
                    disappearingMessages = s.disappearingMessages,
                    relayEnabled         = s.relayEnabled,
                    torEnabled           = s.torEnabled,
                    customRelayUrl       = s.customRelayUrl,
                    notificationsEnabled = s.notificationsEnabled,
                    darkMode             = s.darkMode,
                    proxyEnabled         = s.proxyEnabled,
                    proxyUri             = s.proxyUri,
                )
            )
        }
    }
}
