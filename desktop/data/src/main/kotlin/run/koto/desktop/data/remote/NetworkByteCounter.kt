package run.koto.desktop.data.remote

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import run.koto.desktop.domain.repository.NetworkStatsRepository
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory byte accumulator. Ktor / WebSocket layers call [addSent] /
 * [addReceived] on every transferred buffer; a coroutine flushes the running
 * total to [NetworkStatsRepository] every [flushIntervalMs] so the UI sees
 * up-to-date numbers without thrashing SQLite once per byte.
 *
 * Designed as a process-wide object because Ktor plugins are per-config and
 * it would be awkward to plumb the repo through every send-pipeline. Wire-up
 * happens once in DI via [bindRepository].
 */
object NetworkByteCounter {

    private val log = LoggerFactory.getLogger(NetworkByteCounter::class.java)

    private val sent     = AtomicLong(0L)
    private val received = AtomicLong(0L)

    private var scope: CoroutineScope? = null
    private const val flushIntervalMs = 5_000L

    fun addSent(bytes: Long)     { if (bytes > 0L) sent.addAndGet(bytes) }
    fun addReceived(bytes: Long) { if (bytes > 0L) received.addAndGet(bytes) }

    /** Wire to the repo and start the periodic flush coroutine. Call once at boot. */
    fun bindRepository(repo: NetworkStatsRepository) {
        scope?.cancel()
        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = s
        s.launch {
            while (true) {
                delay(flushIntervalMs)
                val s1 = sent.getAndSet(0L)
                val r1 = received.getAndSet(0L)
                if (s1 > 0L) runCatching { repo.addSent(s1) }
                    .onFailure { log.warn("flush sent failed bytes={}", s1, it) }
                if (r1 > 0L) runCatching { repo.addReceived(r1) }
                    .onFailure { log.warn("flush received failed bytes={}", r1, it) }
            }
        }
    }
}

/**
 * Ktor client plugin that observes outgoing request body length and incoming
 * response Content-Length, feeding both into [NetworkByteCounter]. Added to
 * `bare`/`main`/`storage` clients via [HttpClientFactory.commonConfig].
 */
val ByteCounterPlugin = createClientPlugin("ByteCounter") {
    onRequest { _, content ->
        val len = (content as? OutgoingContent)?.contentLength ?: 0L
        if (len > 0L) NetworkByteCounter.addSent(len)
    }
    onResponse { response ->
        val len = response.headers["Content-Length"]?.toLongOrNull() ?: 0L
        if (len > 0L) NetworkByteCounter.addReceived(len)
    }
}
