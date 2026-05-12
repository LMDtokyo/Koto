package run.koto.desktop.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import run.koto.desktop.data.local.db.KotoDb
import run.koto.desktop.domain.model.AppPreferences
import run.koto.desktop.domain.model.PrivacyPreset
import run.koto.desktop.domain.model.withPreset
import run.koto.desktop.domain.repository.PreferencesRepository

/**
 * SQLDelight-backed implementation. Single-row table; we read or upsert,
 * never delete. New installs return [AppPreferences] defaults until a write
 * lands a row, so the UI never sees null.
 */
class PreferencesRepositoryImpl(
    private val db: KotoDb,
) : PreferencesRepository {

    override fun observe(): Flow<AppPreferences> =
        db.kotoDbQueries.getPreferences()
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { it?.toDomain() ?: AppPreferences() }

    override suspend fun current(): AppPreferences = withContext(Dispatchers.IO) {
        db.kotoDbQueries.getPreferences().executeAsOneOrNull()?.toDomain() ?: AppPreferences()
    }

    override suspend fun setPreset(preset: PrivacyPreset)         = update { it.withPreset(preset) }
    override suspend fun setSendReadReceipts(value: Boolean)      = update { it.copy(sendReadReceipts = value) }
    override suspend fun setSendTyping       (value: Boolean)     = update { it.copy(sendTyping       = value) }
    override suspend fun setShowOnlineStatus (value: Boolean)     = update { it.copy(showOnlineStatus = value) }
    override suspend fun setNotifPreview     (value: Boolean)     = update { it.copy(notifPreview     = value) }
    override suspend fun setDiscoverable     (value: Boolean)     = update { it.copy(discoverable     = value) }

    private suspend fun update(transform: (AppPreferences) -> AppPreferences) {
        withContext(Dispatchers.IO) {
            val next = transform(current())
            db.kotoDbQueries.upsertPreferences(
                privacy_preset      = next.privacyPreset.wireName,
                send_read_receipts  = if (next.sendReadReceipts) 1 else 0,
                send_typing         = if (next.sendTyping)       1 else 0,
                show_online_status  = if (next.showOnlineStatus) 1 else 0,
                notif_preview       = if (next.notifPreview)     1 else 0,
                discoverable        = if (next.discoverable)     1 else 0,
            )
        }
    }
}

private fun run.koto.desktop.data.local.db.App_preferences.toDomain() = AppPreferences(
    privacyPreset    = PrivacyPreset.parse(privacy_preset),
    sendReadReceipts = send_read_receipts == 1L,
    sendTyping       = send_typing        == 1L,
    showOnlineStatus = show_online_status == 1L,
    notifPreview     = notif_preview      == 1L,
    discoverable     = discoverable       == 1L,
)
