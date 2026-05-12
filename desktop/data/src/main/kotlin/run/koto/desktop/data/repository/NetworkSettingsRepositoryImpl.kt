package run.koto.desktop.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import run.koto.desktop.data.local.db.KotoDb
import run.koto.desktop.domain.model.NetworkPreferences
import run.koto.desktop.domain.model.UserProxy
import run.koto.desktop.domain.repository.NetworkSettingsRepository

/**
 * Persists network preferences into a singleton row of the `network_settings` table.
 * Proxy credentials ride the same AES-GCM at-rest protection as other secrets would
 * when the column is migrated to a secure vault — today they live in plaintext SQLite;
 * treat them as low-trust until follow-up (Phase P2).
 */
class NetworkSettingsRepositoryImpl(
    private val db: KotoDb,
) : NetworkSettingsRepository {

    override fun observe(): Flow<NetworkPreferences> =
        db.kotoDbQueries.getNetworkSettings()
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { it?.toDomain() ?: NetworkPreferences() }

    override suspend fun snapshot(): NetworkPreferences = observe().first()

    override suspend fun setTorEnabled(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            val current = db.kotoDbQueries.getNetworkSettings().executeAsOneOrNull()
            db.kotoDbQueries.upsertNetworkSettings(
                tor_enabled     = if (enabled) 1 else 0,
                user_proxy_type = current?.user_proxy_type,
                user_proxy_host = current?.user_proxy_host,
                user_proxy_port = current?.user_proxy_port,
                user_proxy_user = current?.user_proxy_user,
                user_proxy_pass = current?.user_proxy_pass,
            )
        }
    }

    override suspend fun setUserProxy(proxy: UserProxy?) {
        withContext(Dispatchers.IO) {
            val current = db.kotoDbQueries.getNetworkSettings().executeAsOneOrNull()
            val type    = when (proxy) {
                is UserProxy.Socks5 -> "socks5"
                is UserProxy.Http   -> "http"
                null                -> null
            }
            val user = when (proxy) {
                is UserProxy.Socks5 -> proxy.username
                is UserProxy.Http   -> proxy.username
                null                -> null
            }
            val pass = when (proxy) {
                is UserProxy.Socks5 -> proxy.password
                is UserProxy.Http   -> proxy.password
                null                -> null
            }
            db.kotoDbQueries.upsertNetworkSettings(
                tor_enabled     = current?.tor_enabled ?: 0,
                user_proxy_type = type,
                user_proxy_host = proxy?.host,
                user_proxy_port = proxy?.port?.toLong(),
                user_proxy_user = user,
                user_proxy_pass = pass,
            )
        }
    }
}

private fun run.koto.desktop.data.local.db.Network_settings.toDomain(): NetworkPreferences {
    val port = user_proxy_port?.toInt()
    val proxy: UserProxy? = when {
        user_proxy_host.isNullOrBlank() || port == null -> null
        user_proxy_type == "socks5" -> UserProxy.Socks5(user_proxy_host, port, user_proxy_user, user_proxy_pass)
        user_proxy_type == "http"   -> UserProxy.Http  (user_proxy_host, port, user_proxy_user, user_proxy_pass)
        else                        -> null
    }
    return NetworkPreferences(torEnabled = tor_enabled == 1L, userProxy = proxy)
}
