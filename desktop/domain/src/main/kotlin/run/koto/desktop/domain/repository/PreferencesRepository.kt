package run.koto.desktop.domain.repository

import kotlinx.coroutines.flow.Flow
import run.koto.desktop.domain.model.AppPreferences
import run.koto.desktop.domain.model.PrivacyPreset

interface PreferencesRepository {
    fun observe(): Flow<AppPreferences>
    suspend fun current(): AppPreferences

    suspend fun setPreset(preset: PrivacyPreset)
    suspend fun setSendReadReceipts(value: Boolean)
    suspend fun setSendTyping       (value: Boolean)
    suspend fun setShowOnlineStatus (value: Boolean)
    suspend fun setNotifPreview     (value: Boolean)
    suspend fun setDiscoverable     (value: Boolean)
}
