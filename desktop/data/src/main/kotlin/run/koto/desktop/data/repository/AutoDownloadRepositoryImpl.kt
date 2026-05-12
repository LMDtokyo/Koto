package run.koto.desktop.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import run.koto.desktop.data.local.db.KotoDb
import run.koto.desktop.domain.model.AutoDownloadPrefs
import run.koto.desktop.domain.repository.AutoDownloadRepository

class AutoDownloadRepositoryImpl(
    private val db: KotoDb,
) : AutoDownloadRepository {

    override fun observe(): Flow<AutoDownloadPrefs> =
        db.kotoDbQueries.getAutoDownload()
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { it?.toDomain() ?: AutoDownloadPrefs() }

    override suspend fun update(prefs: AutoDownloadPrefs) {
        withContext(Dispatchers.IO) {
            db.kotoDbQueries.upsertAutoDownload(
                photos       = if (prefs.photos) 1 else 0,
                videos       = if (prefs.videos) 1 else 0,
                video_max_mb = prefs.videoMaxMb.toLong(),
                files        = if (prefs.files) 1 else 0,
                file_max_mb  = prefs.fileMaxMb.toLong(),
                voice        = if (prefs.voice) 1 else 0,
            )
        }
    }

    override suspend fun reset() = update(AutoDownloadPrefs())
}

private fun run.koto.desktop.data.local.db.Auto_download.toDomain() = AutoDownloadPrefs(
    photos     = photos == 1L,
    videos     = videos == 1L,
    videoMaxMb = video_max_mb.toInt(),
    files      = files == 1L,
    fileMaxMb  = file_max_mb.toInt(),
    voice      = voice == 1L,
)
