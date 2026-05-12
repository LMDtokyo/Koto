package run.koto.network

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

enum class TorStatus { DISABLED, CONNECTING, CONNECTED, ERROR }

/**
 * Manages an embedded Tor SOCKS5 proxy.
 *
 * Uses the tor binary bundled via `tor-android-binary` in jniLibs.
 * Starts Tor as a child process with a minimal torrc config.
 * SOCKS5 proxy on 127.0.0.1:9050.
 *
 * Fallback: if the bundled binary is not found (dev builds),
 * attempts to connect to an existing SOCKS5 on 9050 (e.g. Orbot).
 */
@Singleton
class TorManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "TorManager"
        private const val SOCKS_PORT = 9050
    }

    private val _status = MutableStateFlow(TorStatus.DISABLED)
    val status: StateFlow<TorStatus> = _status

    private val _bootstrapProgress = MutableStateFlow(0)
    val bootstrapProgress: StateFlow<Int> = _bootstrapProgress

    private var torProcess: Process? = null

    val torProxy: Proxy = Proxy(
        Proxy.Type.SOCKS,
        InetSocketAddress("127.0.0.1", SOCKS_PORT),
    )

    fun createProxiedClient(baseClient: OkHttpClient): OkHttpClient {
        return baseClient.newBuilder()
            .proxy(torProxy)
            .build()
    }

    /**
     * Start embedded Tor or connect to existing SOCKS5 proxy.
     * Returns true when the proxy is reachable.
     */
    suspend fun enable(): Boolean = withContext(Dispatchers.IO) {
        _status.value = TorStatus.CONNECTING
        _bootstrapProgress.value = 0

        try {
            // Try to start embedded Tor binary
            val started = startEmbeddedTor()
            if (!started) {
                // Fallback: check if SOCKS5 is already available (Orbot, etc.)
                Log.d(TAG, "Embedded Tor not available, checking existing SOCKS5...")
            }

            // Wait for SOCKS5 proxy to become reachable (up to 60s)
            val deadline = System.currentTimeMillis() + 60_000
            while (System.currentTimeMillis() < deadline) {
                if (isSocksReachable()) {
                    _status.value = TorStatus.CONNECTED
                    _bootstrapProgress.value = 100
                    Log.i(TAG, "Tor SOCKS5 proxy connected on port $SOCKS_PORT")
                    return@withContext true
                }
                _bootstrapProgress.value = ((System.currentTimeMillis() - (deadline - 60_000)) * 90 / 60_000).toInt().coerceIn(0, 90)
                delay(1000)
            }

            _status.value = TorStatus.ERROR
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable Tor", e)
            _status.value = TorStatus.ERROR
            false
        }
    }

    /** Stop the embedded Tor daemon. */
    fun disable() {
        torProcess?.let {
            it.destroy()
            torProcess = null
            Log.i(TAG, "Tor process stopped")
        }
        _status.value = TorStatus.DISABLED
        _bootstrapProgress.value = 0
    }

    /**
     * Try to start the tor binary from native libs.
     * tor-android-binary packages the tor executable as a native .so in jniLibs.
     */
    private fun startEmbeddedTor(): Boolean {
        try {
            val torDir = File(context.filesDir, "tor")
            torDir.mkdirs()

            // Write minimal torrc
            val torrc = File(torDir, "torrc")
            torrc.writeText(
                """
                SocksPort $SOCKS_PORT
                DataDirectory ${torDir.absolutePath}/data
                CacheDirectory ${context.cacheDir.absolutePath}/tor
                AvoidDiskWrites 1
                Log notice stderr
                """.trimIndent()
            )

            // Find tor binary — bundled as libtor.so in native libs
            val nativeLibDir = context.applicationInfo.nativeLibraryDir
            val torBinary = File(nativeLibDir, "libtor.so")

            if (!torBinary.exists()) {
                Log.w(TAG, "Tor binary not found at ${torBinary.absolutePath}")
                return false
            }

            // Ensure data dir exists
            File(torDir, "data").mkdirs()

            // Start Tor process
            val process = ProcessBuilder(torBinary.absolutePath, "-f", torrc.absolutePath)
                .directory(torDir)
                .redirectErrorStream(true)
                .start()

            torProcess = process

            // Log output in background thread — wrap in try/catch because
            // InputStream.read() throws InterruptedIOException when process is destroyed.
            Thread {
                try {
                    process.inputStream.bufferedReader().forEachLine { line ->
                        Log.d(TAG, line)
                        // Parse bootstrap progress from Tor log
                        val match = Regex("""Bootstrapped (\d+)%""").find(line)
                        match?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { pct ->
                            _bootstrapProgress.value = pct
                        }
                    }
                } catch (e: java.io.IOException) {
                    // Expected when process is destroyed — not an error
                    Log.d(TAG, "Tor log reader stopped: ${e.message}")
                } catch (e: Exception) {
                    Log.w(TAG, "Tor log reader error", e)
                }
            }.start()

            Log.i(TAG, "Tor process started (PID: ${getPid(process)})")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start embedded Tor", e)
            return false
        }
    }

    private fun isSocksReachable(): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", SOCKS_PORT), 2000)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    @Suppress("unused")
    private fun getPid(process: Process): Long = -1L
}
