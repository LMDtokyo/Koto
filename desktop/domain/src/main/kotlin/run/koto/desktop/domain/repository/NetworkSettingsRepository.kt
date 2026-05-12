package run.koto.desktop.domain.repository

import kotlinx.coroutines.flow.Flow
import run.koto.desktop.domain.model.NetworkPreferences
import run.koto.desktop.domain.model.UserProxy

interface NetworkSettingsRepository {
    fun observe(): Flow<NetworkPreferences>
    suspend fun snapshot(): NetworkPreferences
    suspend fun setTorEnabled(enabled: Boolean)
    suspend fun setUserProxy(proxy: UserProxy?)
}
