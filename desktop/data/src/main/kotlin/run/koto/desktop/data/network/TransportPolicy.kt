package run.koto.desktop.data.network

import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.ProxyConfig
import io.ktor.http.Url
import org.slf4j.LoggerFactory
import run.koto.desktop.domain.model.NetworkPreferences
import run.koto.desktop.domain.model.TorState
import run.koto.desktop.domain.model.UserProxy

/**
 * Decides the proxy for every outbound request at startup. Priority:
 *
 *   1. Tor — when the daemon is RUNNING and a SOCKS port is known
 *   2. User proxy — SOCKS5 or HTTP supplied by the user
 *   3. Direct — nothing configured
 *
 * Toggling Tor / user proxy at runtime is intentionally out of scope for v1: the
 * HttpClients are singletons (via Koin), and rebuilding them mid-request caused more
 * bugs than it's worth. The user flips a toggle → we ask them to restart. Matches
 * Signal Desktop's UX.
 */
object TransportPolicy {

    private val log = LoggerFactory.getLogger(TransportPolicy::class.java)

    fun resolve(
        torState    : TorState,
        torSocksPort: Int?,
        prefs       : NetworkPreferences,
    ): ProxyConfig? {
        if (torState == TorState.RUNNING && torSocksPort != null) {
            log.info("transport: tor ({}:{})", "127.0.0.1", torSocksPort)
            return ProxyBuilder.socks("127.0.0.1", torSocksPort)
        }
        val user = prefs.userProxy
        if (user != null) {
            log.info("transport: user proxy ({}:{})", user.host, user.port)
            return userToKtor(user)
        }
        log.info("transport: direct")
        return null
    }

    fun userToKtor(p: UserProxy): ProxyConfig = when (p) {
        is UserProxy.Socks5 -> ProxyBuilder.socks(p.host, p.port)
        is UserProxy.Http   -> ProxyBuilder.http(Url("http://${p.host}:${p.port}"))
    }
}
