package run.koto.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import run.koto.data.prefs.AccountPrefs
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adds `Authorization: Bearer <access_token>` to every outgoing request.
 *
 * Security / performance notes:
 *
 *  - The token is held in an [AtomicReference] so the interceptor never
 *    blocks the OkHttp dispatcher thread on DataStore I/O. The first
 *    request initialises it via a short [runBlocking]; subsequent
 *    requests read the cached value.
 *
 *  - An observer coroutine watches [AccountPrefs.isRegisteredFlow] and
 *    every change to the stored access token so the cached value stays
 *    in sync when [TokenAuthenticator] rotates the tokens on 401.
 *
 *  - The interceptor never logs the token. OkHttpLoggingInterceptor in
 *    release builds is NONE.
 *
 *  - Requests explicitly opting out (auth endpoints) can set a header
 *    `X-Koto-No-Auth: 1` to skip the attachment — used by the refresh
 *    call which must never send the stale access token.
 */
@Singleton
class AuthHeaderInterceptor @Inject constructor(
    private val accountPrefs: AccountPrefs,
) : Interceptor {

    private val cachedToken = AtomicReference<String?>(null)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Seed the cache + follow token changes so TokenAuthenticator rotations
        // propagate without needing an I/O hit on the request path.
        scope.launch {
            cachedToken.set(accountPrefs.getAccessToken())
        }
        scope.launch {
            // Re-read the token every time the account prefs stream emits.
            accountPrefs.accessTokenFlow().collect { token ->
                cachedToken.set(token)
            }
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // Opt-out marker for the refresh endpoint.
        if (request.header(NO_AUTH_HEADER) != null) {
            val stripped = request.newBuilder().removeHeader(NO_AUTH_HEADER).build()
            return chain.proceed(stripped)
        }

        // Get current token — cached fast path, falls back to DataStore on first call.
        val token = cachedToken.get()
            ?: runBlocking { accountPrefs.getAccessToken() }?.also { cachedToken.set(it) }

        val authed = if (token != null) {
            request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            request
        }
        return chain.proceed(authed)
    }

    companion object {
        const val NO_AUTH_HEADER = "X-Koto-No-Auth"
    }
}
