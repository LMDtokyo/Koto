package run.koto.desktop.domain.repository

import kotlinx.coroutines.flow.Flow
import run.koto.desktop.domain.model.NetworkStats

interface NetworkStatsRepository {
    fun observe(): Flow<NetworkStats>
    suspend fun addSent(bytes: Long)
    suspend fun addReceived(bytes: Long)
    suspend fun reset()
}
