package run.koto.desktop.data.network

import io.matthewnelson.kmp.tor.resource.exec.tor.ResourceLoaderTorExec
import io.matthewnelson.kmp.tor.runtime.Action
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent
import io.matthewnelson.kmp.tor.runtime.TorRuntime
import io.matthewnelson.kmp.tor.runtime.core.OnEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import run.koto.desktop.domain.model.TorState
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Embedded Tor daemon managed via the kmp-tor library.
 *
 *   - `kmp-tor-runtime`          drives the daemon lifecycle (start / stop / event bus).
 *   - `kmp-tor-resource-exec-tor` ships per-platform tor executables and extracts them on
 *     first launch into [workDir]/tor-resource.
 *
 * [start] suspends until the daemon reports `Bootstrapped 100%`. Returning sooner would
 * let the first HTTPS call through a half-open SOCKS port and fail with
 * `general SOCKS server failure`. The SocksPort is requested with `auto` so the OS picks
 * a free TCP port — the selected port is parsed from the
 * `Opened Socks listener on 127.0.0.1:XXXXX` log line.
 */
class TorManager(
    workDir  : Path,
    cacheDir : Path,
) {
    private val log = LoggerFactory.getLogger(TorManager::class.java)

    private val _state     = MutableStateFlow(TorState.DISABLED)
    val state: StateFlow<TorState> = _state.asStateFlow()

    private val _bootstrap = MutableStateFlow(0)
    val bootstrap: StateFlow<Int> = _bootstrap.asStateFlow()

    private val _socksPort = MutableStateFlow<Int?>(null)
    val socksPort: StateFlow<Int?> = _socksPort.asStateFlow()

    private val environment: TorRuntime.Environment = run {
        Files.createDirectories(workDir)
        Files.createDirectories(cacheDir)
        TorRuntime.Environment.Builder(
            /* workDirectory  */ workDir.toFile(),
            /* cacheDirectory */ cacheDir.toFile(),
            /* loader         */ ResourceLoaderTorExec::getOrCreate,
        )
    }

    @Volatile private var runtime: TorRuntime? = null

    /**
     * Starts the daemon. Suspends up to [timeoutMillis] waiting for bootstrap 100%.
     * On failure the state is [TorState.FAILED]; caller decides whether to fall back
     * to direct/user-proxy or abort.
     */
    suspend fun start(timeoutMillis: Long = DEFAULT_BOOTSTRAP_TIMEOUT_MS): Boolean =
        withContext(Dispatchers.IO) {
            if (_state.value == TorState.RUNNING) return@withContext true
            _state.value     = TorState.STARTING
            _bootstrap.value = 0
            _socksPort.value = null

            val rt = TorRuntime.Builder(environment) {
                observerStatic(RuntimeEvent.LOG.INFO, OnEvent.Executor.Immediate) { line ->
                    val text = line.toString()
                    log.info("tor: {}", text)
                    parseBootstrapPercent(text)?.let { _bootstrap.value = it }
                    parseSocksPort(text)?.let { _socksPort.value = it }
                }
                observerStatic(RuntimeEvent.LOG.WARN, OnEvent.Executor.Immediate) { line ->
                    log.warn("tor: {}", line)
                }
                observerStatic(RuntimeEvent.ERROR, OnEvent.Executor.Immediate) { t ->
                    log.error("tor runtime error", t as? Throwable)
                }
                // SocksPort defaults to 9050. If 9050 is taken on the host, kmp-tor's
                // runtime auto-fallback kicks in and picks a free port. Either way we
                // learn the actual port number via the "Opened Socks listener" log line
                // parsed below.
            }
            runtime = rt

            val started = runCatching {
                suspendCancellableCoroutine<Boolean> { cont ->
                    rt.enqueue(
                        Action.StartDaemon,
                        { t -> if (cont.isActive) cont.resumeWithException(t) },
                        { if (cont.isActive) cont.resume(true) },
                    )
                    cont.invokeOnCancellation { rt.enqueue(Action.StopDaemon, { }, { }) }
                }
            }.onFailure { log.error("Action.StartDaemon failed", it) }.getOrElse { false }

            if (!started) {
                _state.value = TorState.FAILED
                return@withContext false
            }

            // Wait for bootstrap and a parsed SOCKS port (both are logged by the daemon).
            val deadline = System.currentTimeMillis() + timeoutMillis
            while (System.currentTimeMillis() < deadline &&
                   (_bootstrap.value < 100 || _socksPort.value == null)) {
                delay(200)
            }

            if (_bootstrap.value < 100 || _socksPort.value == null) {
                log.warn(
                    "tor did not bootstrap in time (progress={}%, socks={})",
                    _bootstrap.value, _socksPort.value,
                )
                _state.value = TorState.FAILED
                return@withContext false
            }

            _state.value = TorState.RUNNING
            log.info("tor ready on 127.0.0.1:{}", _socksPort.value)
            true
        }

    suspend fun stop() = withContext(Dispatchers.IO) {
        val rt = runtime ?: return@withContext
        runCatching {
            suspendCancellableCoroutine<Unit> { cont ->
                rt.enqueue(
                    Action.StopDaemon,
                    { t -> if (cont.isActive) cont.resumeWithException(t) },
                    { if (cont.isActive) cont.resume(Unit) },
                )
            }
        }.onFailure { log.warn("tor stop failed", it) }
        runtime = null
        _socksPort.value = null
        _bootstrap.value = 0
        _state.value     = TorState.DISABLED
    }

    private fun parseBootstrapPercent(line: String): Int? =
        BOOTSTRAP_REGEX.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()

    private fun parseSocksPort(line: String): Int? =
        SOCKS_LISTENER_REGEX.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()

    companion object {
        private const val DEFAULT_BOOTSTRAP_TIMEOUT_MS = 60_000L
        private val BOOTSTRAP_REGEX       = Regex("""Bootstrapped\s+(\d+)%""")
        private val SOCKS_LISTENER_REGEX  = Regex("""Opened Socks listener on 127\.0\.0\.1:(\d+)""")
    }
}
