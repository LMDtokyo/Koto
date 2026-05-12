package run.koto.desktop.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import run.koto.desktop.data.local.db.KotoDb
import run.koto.desktop.domain.model.NetworkStats
import run.koto.desktop.domain.repository.NetworkStatsRepository

class NetworkStatsRepositoryImpl(
    private val db: KotoDb,
) : NetworkStatsRepository {

    override fun observe(): Flow<NetworkStats> =
        db.kotoDbQueries.getNetworkStats()
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { it?.toDomain() ?: NetworkStats() }

    override suspend fun addSent(bytes: Long) {
        if (bytes <= 0L) return
        withContext(Dispatchers.IO) {
            db.kotoDbQueries.ensureNetworkStatsRow(System.currentTimeMillis())
            db.kotoDbQueries.addNetworkSent(delta = bytes)
        }
    }

    override suspend fun addReceived(bytes: Long) {
        if (bytes <= 0L) return
        withContext(Dispatchers.IO) {
            db.kotoDbQueries.ensureNetworkStatsRow(System.currentTimeMillis())
            db.kotoDbQueries.addNetworkReceived(delta = bytes)
        }
    }

    override suspend fun reset() {
        withContext(Dispatchers.IO) {
            db.kotoDbQueries.resetNetworkStats(System.currentTimeMillis())
        }
    }
}

private fun run.koto.desktop.data.local.db.Network_stats.toDomain() = NetworkStats(
    sentBytes     = sent_bytes,
    receivedBytes = received_bytes,
    sinceAt       = since_at,
)
