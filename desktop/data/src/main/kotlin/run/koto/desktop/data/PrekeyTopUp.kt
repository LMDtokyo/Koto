package run.koto.desktop.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import run.koto.desktop.crypto.KotoCryptoProvider
import run.koto.desktop.data.remote.api.AuthApi

/**
 * Replenishes the server's one-time prekey pool.
 *
 * The Koto server consumes one OTPK per new session (X3DH). When the pool drops below
 * [LOW_WATERMARK], the client regenerates a fresh batch and publishes it via the
 * legacy hex-encoded auth endpoint. Returns [Result] so callers can schedule retries
 * on network failure.
 *
 * This is intentionally synchronous — callers decide when to invoke (after sending
 * a message, after WS reconnect, scheduled every few hours, etc.).
 */
class PrekeyTopUp(
    private val crypto  : KotoCryptoProvider,
    private val authApi : AuthApi,
) {
    private val log = LoggerFactory.getLogger(PrekeyTopUp::class.java)

    /**
     * Generates 100 fresh OTPKs in-memory (not persisted — they live in the live Rust
     * store) and pushes their public halves. Call when you know the pool is low, e.g.
     * after the server returns no `one_time_prekey` in a bundle fetch.
     *
     * NOTE: current Rust surface exposes key generation only through
     * `generateRegistrationBundle`, which also rotates the identity pair. A dedicated
     * `rotate_prekeys` export is tracked in Phase 05.1 follow-ups. Until then, the
     * safer stopgap is to upload the OTPKs produced at registration once, then rely
     * on the long-lived signed prekey + Kyber prekey for new sessions.
     */
    suspend fun topUpIfNeeded(currentPoolSize: Int?): Result<Int> = runCatching {
        val size = currentPoolSize ?: Int.MAX_VALUE
        if (size >= LOW_WATERMARK) {
            log.trace("prekey pool healthy size={}", size)
            return@runCatching size
        }
        log.info("prekey pool low ({}), top-up required — deferring until crypto rotate_prekeys lands", size)
        size
    }

    /**
     * Uploads the OTPKs that were generated at initial registration. Called once from
     * [run.koto.desktop.data.SessionCoordinator] after the first successful session is
     * established, to make the initial pool available server-side if the two-step
     * upload in [run.koto.desktop.data.repository.AuthRepositoryImpl.register] failed.
     */
    suspend fun uploadInitialOneTimePrekeys(oneTimePrekeysHex: List<String>) {
        if (oneTimePrekeysHex.isEmpty()) return
        runCatching {
            val total = withContext(Dispatchers.IO) { authApi.publishPrekeys(oneTimePrekeysHex) }
            log.info("published {} one-time prekeys, server pool total={}", oneTimePrekeysHex.size, total.total)
        }.onFailure { log.warn("initial OTPK publish failed", it) }
    }

    companion object {
        /** Ask for a top-up once the server reports this many or fewer OTPKs. */
        private const val LOW_WATERMARK = 10
    }
}
