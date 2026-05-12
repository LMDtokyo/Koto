package run.koto.desktop.domain.repository

import kotlinx.coroutines.flow.Flow
import run.koto.desktop.domain.model.AutoDownloadPrefs

interface AutoDownloadRepository {
    fun observe(): Flow<AutoDownloadPrefs>
    suspend fun update(prefs: AutoDownloadPrefs)
    suspend fun reset()
}
