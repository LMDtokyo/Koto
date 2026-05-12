package run.koto.network

import android.util.Log
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import run.koto.data.prefs.AccountPrefs
import run.koto.data.remote.api.AuthApi
import run.koto.data.remote.api.RefreshRequest
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * OkHttp [Authenticator] that transparently refreshes the JWT access token
 * on 401 Unauthorized and retries the original request.
 *
 * Security-critical invariants:
 *
 *  1. **No recursion on the refresh endpoint.** The refresh call itself must
 *     go out through a separate OkHttpClient that does NOT install this
 *     authenticator, otherwise a failed refresh would re-enter and loop.
 *     We inject [AuthApi] from the dedicated refresh-chain provider
 *     (`provideRefreshAuthApi` in NetworkModule).
 *
 *  2. **Single-flight refresh.** A [Mutex] serialises concurrent refreshes so
 *     two parallel 401s share one refresh roundtrip. The second caller sees
 *     the token already rotated and retries with the fresh one.
 *
 *  3. **Stale-token detection inside the lock.** After acquiring the mutex we
 *     re-read the current access token. If it differs from the token on the
 *     failing request we know a concurrent refresh already completed —
 *     retry with the fresh token instead of refreshing again.
 *
 *  4. **Refresh-token rotation.** The backend returns a *new* refresh token
 *     every time; we persist it atomically together with the new access
 *     token. A stolen refresh token becomes invalid on first legitimate use.
 *
 *  5. **Bail-out on repeated failure.** If refresh itself returns 401 or
 *     we've already tried once in this request chain, we clear the stored
 *     session and return null — the UI observes the logged-out state via
 *     [AccountPrefs] and drops the user back to onboarding.
 *
 *  6. **Never attach Authorization to the refresh call itself.** The bare
 *     OkHttpClient used by the refresh AuthApi has no auth interceptor, so
 *     the refresh payload is the only credential sent.
 *
 *  7. **No token logging.** [runBlocking] only wraps the suspend call — we
 *     never pass tokens through Log.* and OkHttpLoggingInterceptor runs at
 *     NONE level in release builds.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val accountPrefs : AccountPrefs,
    /** Must come from a bare OkHttpClient WITHOUT this authenticator. */
    @RefreshAuthApi private val refreshApi: Provider<AuthApi>,
) : Authenticator {

    private val refreshMutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        // If the failing request had no Authorization header, we can't help —
        // it was already anonymous and the server rejected it for some other reason.
        val failedAuth = response.request.header("Authorization") ?: return null

        // Prevent infinite loops: if this request chain has already been through
        // the authenticator once, give up.
        if (responseCount(response) >= 2) {
            Log.w(TAG, "Refresh already attempted — clearing session")
            runBlocking { accountPrefs.clearSession() }
            return null
        }

        // Extract the token that was attached to the failed request so we can
        // tell inside the lock whether another thread already rotated it.
        val failedToken = failedAuth.removePrefix("Bearer ").trim()

        return runBlocking {
            refreshMutex.withLock {
                // Re-check: maybe another parallel request already refreshed while
                // we were waiting on the lock.
                val currentToken = accountPrefs.getAccessToken()
                if (currentToken != null && currentToken != failedToken) {
                    Log.d(TAG, "Token already refreshed by another request — retrying")
                    return@withLock response.request.newBuilder()
                        .header("Authorization", "Bearer $currentToken")
                        .build()
                }

                val currentRefresh = accountPrefs.getRefreshToken()
                if (currentRefresh.isNullOrEmpty()) {
                    Log.w(TAG, "No refresh token — clearing session")
                    accountPrefs.clearSession()
                    return@withLock null
                }

                val newTokens = try {
                    refreshApi.get().refresh(RefreshRequest(refresh_token = currentRefresh))
                } catch (e: retrofit2.HttpException) {
                    if (e.code() == 401) {
                        Log.w(TAG, "Refresh token rejected — clearing session")
                        accountPrefs.clearSession()
                    } else {
                        Log.e(TAG, "Refresh HTTP ${e.code()}")
                    }
                    return@withLock null
                } catch (e: Exception) {
                    Log.e(TAG, "Refresh network failure: ${e.javaClass.simpleName}")
                    // Transient network error — don't clear session, just fail this
                    // request so the caller can retry when connectivity returns.
                    return@withLock null
                }

                accountPrefs.saveTokens(
                    accessToken  = newTokens.access_token,
                    refreshToken = newTokens.refresh_token,
                )

                response.request.newBuilder()
                    .header("Authorization", "Bearer ${newTokens.access_token}")
                    .build()
            }
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    companion object {
        private const val TAG = "TokenAuthenticator"
    }
}

/** Qualifier for the bare AuthApi used only by [TokenAuthenticator]. */
@javax.inject.Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RefreshAuthApi
