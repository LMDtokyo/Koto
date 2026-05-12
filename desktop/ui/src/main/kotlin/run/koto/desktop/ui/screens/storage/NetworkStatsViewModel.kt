package run.koto.desktop.ui.screens.storage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import run.koto.desktop.domain.model.NetworkStats
import run.koto.desktop.domain.repository.NetworkStatsRepository

class NetworkStatsViewModel(
    private val repo: NetworkStatsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val state: StateFlow<NetworkStats> = repo.observe()
        .stateIn(scope, SharingStarted.Eagerly, NetworkStats())

    fun reset() = scope.launch { repo.reset() }

    fun close() = scope.cancel()
}
