package run.koto.desktop.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.ProxyConfig
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.cio.CIOEngineConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import run.koto.desktop.domain.DomainError
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager

/**
 * Three Ktor clients live in the DI graph:
 *
 *   - `bare`: no Auth plugin. Used by AuthApi for token-rotation endpoints (register,
 *             refresh, revoke) that cannot depend back on a Bearer-authenticated client.
 *   - `main`: Bearer Auth plugin, defaultRequest host, WebSocket plugin. Used by every
 *             authenticated API.
 *   - `storage`: no Auth, no defaultRequest host. Only used for presigned-URL PUTs against
 *             object storage (MinIO) — the URL carries its own SigV4 credentials.
 *
 * TLS: if [TlsPolicy.pinnedSpkiSha256] is non-empty, all outbound HTTPS traffic for the
 * main/bare clients goes through [PinnedTrustManager]. The [TlsPolicy.bypassHosts] set
 * skips pinning for localhost so development against `docker compose up` works without a
 * trusted cert. Production hosts are always pinned.
 */
object HttpClientFactory {

    private val logger = LoggerFactory.getLogger(HttpClientFactory::class.java)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient         = true
        encodeDefaults    = true
        explicitNulls     = false
    }

    data class Endpoints(
        val baseHost : String,
        val baseTls  : Boolean,
        val restPort : Int = if (baseTls) 443  else 8080,
        val wsPort   : Int = if (baseTls) 9443 else 9080,
    )

    /**
     * Trust policy for outbound HTTPS.
     *
     * [pinnedSpkiSha256] — SHA-256 hex digests of the server certificate's Subject Public
     * Key Info (SPKI). Pinning the public key rather than a specific cert survives routine
     * cert renewal — only a key rotation on the server requires a client update.
     *
     * [bypassHosts] — hostnames exempt from pinning (typically `localhost` / `127.0.0.1`
     * for dev). Never list production hosts here.
     */
    data class TlsPolicy(
        val pinnedSpkiSha256 : Set<String> = emptySet(),
        val bypassHosts      : Set<String> = setOf("localhost", "127.0.0.1"),
    )

    @Serializable
    private data class ErrorBody(val error: String? = null)

    /**
     * Logger that wraps slf4j and strips bearer tokens from URL query strings
     * before they reach disk — without this `?token=...` (used by the WebSocket
     * upgrade path) would land in any access-style log line emitted at HEADERS
     * or ALL log levels.
     */
    private object SensitiveQueryStrippingLogger : Logger {
        private val log = LoggerFactory.getLogger("KotoHttpClient")
        private val tokenParam = Regex("""([?&]token=)[^&\s]+""")
        override fun log(message: String) {
            log.info(tokenParam.replace(message, "$1<redacted>"))
        }
    }

    fun bare(
        endpoints : Endpoints,
        tls       : TlsPolicy = TlsPolicy(),
        proxy     : ProxyConfig? = null,
    ): HttpClient = HttpClient(CIO) {
        commonConfig(endpoints, tls, proxy)
    }

    fun storage(proxy: ProxyConfig? = null): HttpClient = HttpClient(CIO) {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 120_000
            socketTimeoutMillis  = 120_000
        }
        install(Logging) { level = LogLevel.INFO }
        engine {
            proxy?.let { this.proxy = it }
        }
    }

    fun main(
        endpoints     : Endpoints,
        tls           : TlsPolicy = TlsPolicy(),
        proxy         : ProxyConfig? = null,
        tokenProvider : () -> BearerTokens?,
        refreshTokens : suspend () -> BearerTokens?,
    ): HttpClient = HttpClient(CIO) {
        commonConfig(endpoints, tls, proxy)
        install(WebSockets) { pingIntervalMillis = 20_000 }
        install(Auth) {
            bearer {
                loadTokens    { tokenProvider() }
                refreshTokens { refreshTokens() }
                sendWithoutRequest { true }
            }
        }
    }

    private fun HttpClientConfig<CIOEngineConfig>.commonConfig(
        endpoints: Endpoints,
        tls: TlsPolicy,
        proxy: ProxyConfig?,
    ) {
        expectSuccess = false

        // Approximate byte counters for the Settings → Network screen. We
        // observe outgoing request body length and the response Content-Length
        // header — close enough for a UI display without buffering bodies.
        install(ByteCounterPlugin)

        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 30_000
            socketTimeoutMillis  = 60_000
        }
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 3)
            retryOnException(maxRetries = 2, retryOnTimeout = true)
            retryIf(maxRetries = 3) { _, response -> response.status.value == 429 }
            exponentialDelay(base = 2.0, maxDelayMs = 10_000)
        }
        // HTTP request/response logging is OFF by default — access-log style output leaks
        // path parameters (accountId, messageId, fileId). Opt in for local debugging via
        // `-Dkoto.http.log=INFO` / `HEADERS` / `ALL`.
        val logLevel = runCatching {
            LogLevel.valueOf((System.getProperty("koto.http.log") ?: "NONE").uppercase())
        }.getOrDefault(LogLevel.NONE)
        install(Logging) {
            level  = logLevel
            logger = SensitiveQueryStrippingLogger
            sanitizeHeader { header -> header.equals("Authorization", ignoreCase = true) ||
                                       header.equals("Sec-WebSocket-Protocol", ignoreCase = true) ||
                                       header.equals("Cookie", ignoreCase = true) ||
                                       header.equals("Set-Cookie", ignoreCase = true) }
        }
        defaultRequest {
            url {
                protocol = if (endpoints.baseTls) URLProtocol.HTTPS else URLProtocol.HTTP
                host     = endpoints.baseHost
                port     = endpoints.restPort
            }
        }
        engine {
            proxy?.let { this.proxy = it }
            if (tls.pinnedSpkiSha256.isNotEmpty()) {
                https {
                    val pinned = PinnedTrustManager(tls.pinnedSpkiSha256, tls.bypassHosts)
                    trustManager = pinned
                    val ctx = SSLContext.getInstance("TLS")
                    ctx.init(null, arrayOf<TrustManager>(pinned), null)
                    serverName = endpoints.baseHost
                }
            }
        }
        HttpResponseValidator {
            validateResponse { response ->
                val status = response.status
                if (status.isSuccess()) return@validateResponse
                // 101 Switching Protocols is the WebSocket upgrade handshake — treating it as
                // a failure throws DomainError.Network and traps the client in a reconnect loop.
                if (status.value == 101) return@validateResponse
                // Server error strings are logged for debugging but not propagated to the UI —
                // the client presents fixed, low-information messages to avoid exposing
                // server internals (table names, rate-limit thresholds, stack hints).
                val serverMsg = runCatching { response.body<ErrorBody>().error }.getOrNull()
                logger.warn("http {} from {}: {}", status.value, response.call.request.url.encodedPath, serverMsg)
                throw when (status) {
                    HttpStatusCode.Unauthorized,
                    HttpStatusCode.Forbidden      -> DomainError.Unauthorized("not authorised")
                    HttpStatusCode.NotFound       -> DomainError.NotFound("not found")
                    HttpStatusCode.Conflict       -> DomainError.AlreadyExists(serverMsg ?: "conflict")
                    HttpStatusCode.BadRequest     -> DomainError.InvalidInput(serverMsg ?: "invalid request")
                    HttpStatusCode.TooManyRequests-> DomainError.Network("rate limited")
                    else                           -> DomainError.Network("network error (${status.value})")
                }
            }
        }
    }
}
