package run.koto.network

import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import run.koto.BuildConfig
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Server connectivity state — drives the "Нет соединения с сервером" banner.
 *
 * Strategy:
 *   1. Periodic health check every 10s against `/health` via the active
 *      transport (direct / user proxy / Tor — picked by TorAwareCallFactory).
 *   2. Any API call that fails with network error flips state to DISCONNECTED
 *      and triggers an immediate re-check.
 *   3. Successful API call flips state back to CONNECTED.
 *
 * Exposed as a [StateFlow] so any Composable can observe it via
 * `connectivityManager.status.collectAsState()`.
 */
enum class ServerStatus { UNKNOWN, CONNECTED, DISCONNECTED, CHECKING }

@Singleton
class ConnectivityManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val callFactory: TorAwareCallFactory,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow(ServerStatus.UNKNOWN)
    val status: StateFlow<ServerStatus> = _status

    /** Seconds since the last successful health ping. `-1` if never connected. */
    private val _lastSeenMs = MutableStateFlow(-1L)
    val lastSeenMs: StateFlow<Long> = _lastSeenMs

    private val checking = AtomicBoolean(false)

    init {
        // Start periodic polling
        scope.launch {
            // First check immediately
            check()
            while (isActive) {
                kotlinx.coroutines.delay(10_000)
                check()
            }
        }
    }

    /** Manually trigger a health check (e.g. after a failed API call). */
    fun check() {
        if (!checking.compareAndSet(false, true)) return  // already in-flight
        if (_status.value != ServerStatus.CONNECTED) {
            _status.value = ServerStatus.CHECKING
        }

        val request = Request.Builder()
            .url(BuildConfig.BASE_URL + "/health")
            .get()
            .build()

        callFactory.activeClient()
            .newCall(request)
            .enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    _status.value = ServerStatus.DISCONNECTED
                    checking.set(false)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { r ->
                        if (r.isSuccessful) {
                            _status.value = ServerStatus.CONNECTED
                            _lastSeenMs.value = System.currentTimeMillis()
                        } else {
                            _status.value = ServerStatus.DISCONNECTED
                        }
                    }
                    checking.set(false)
                }
            })
    }

    /** Call this from API error handlers to update status immediately. */
    fun reportFailure() {
        _status.value = ServerStatus.DISCONNECTED
        check()
    }

    fun reportSuccess() {
        if (_status.value != ServerStatus.CONNECTED) {
            _status.value = ServerStatus.CONNECTED
        }
        _lastSeenMs.value = System.currentTimeMillis()
    }
}
