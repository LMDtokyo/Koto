package run.koto.network

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Proxy

/**
 * A [Call.Factory] that routes HTTP requests through the highest-priority
 * available transport:
 *
 *   1. Tor (if [TorManager.status] is CONNECTED)
 *   2. User proxy (if [ProxyManager.activeProxy] is non-null)
 *   3. Direct
 *
 * Each transport's [OkHttpClient] is cached and rebuilt only when the
 * underlying [Proxy] instance changes. WebSocket reconnections also pick
 * up the current client via [activeClient].
 */
class TorAwareCallFactory(
    private val torManager   : TorManager,
    private val proxyManager : ProxyManager,
    baseClient               : OkHttpClient,
) : Call.Factory {

    private val directClient: OkHttpClient = baseClient

    @Volatile private var proxiedClient: OkHttpClient? = null
    @Volatile private var lastProxy    : Proxy?        = null

    /** Returns the currently-active client. */
    fun activeClient(): OkHttpClient {
        // Priority 1: Tor
        if (torManager.status.value == TorStatus.CONNECTED) {
            return clientFor(torManager.torProxy)
        }
        // Priority 2: User proxy
        val userProxy = proxyManager.activeProxy.value
        if (userProxy != null) {
            return clientFor(userProxy.toJavaProxy())
        }
        // Priority 3: Direct
        return directClient
    }

    private fun clientFor(proxy: Proxy): OkHttpClient {
        val cached = proxiedClient
        if (cached != null && lastProxy == proxy) return cached
        val newClient = directClient.newBuilder()
            .proxy(proxy)
            .build()
        proxiedClient = newClient
        lastProxy = proxy
        return newClient
    }

    override fun newCall(request: Request): Call = activeClient().newCall(request)
}
