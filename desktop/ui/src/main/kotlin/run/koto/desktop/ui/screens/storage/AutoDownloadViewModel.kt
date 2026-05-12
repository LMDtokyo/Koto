package run.koto.desktop.ui.screens.storage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import run.koto.desktop.domain.model.AutoDownloadPrefs
import run.koto.desktop.domain.repository.AutoDownloadRepository

class AutoDownloadViewModel(
    private val repo: AutoDownloadRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val state: StateFlow<AutoDownloadPrefs> = repo.observe()
        .stateIn(scope, SharingStarted.Eagerly, AutoDownloadPrefs())

    fun togglePhotos(value: Boolean) = patch { it.copy(photos = value) }
    fun toggleVideos(value: Boolean) = patch { it.copy(videos = value) }
    fun toggleFiles (value: Boolean) = patch { it.copy(files  = value) }
    fun toggleVoice (value: Boolean) = patch { it.copy(voice  = value) }
    fun setVideoMaxMb(mb: Int)       = patch { it.copy(videoMaxMb = mb.coerceAtLeast(0)) }
    fun setFileMaxMb (mb: Int)       = patch { it.copy(fileMaxMb  = mb.coerceAtLeast(0)) }
    fun reset()                      = scope.launch { repo.reset() }

    private fun patch(transform: (AutoDownloadPrefs) -> AutoDownloadPrefs) = scope.launch {
        repo.update(transform(state.value))
    }

    fun close() = scope.cancel()
}
