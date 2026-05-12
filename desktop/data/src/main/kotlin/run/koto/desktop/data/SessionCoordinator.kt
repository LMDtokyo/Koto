package run.koto.desktop.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import run.koto.desktop.crypto.KotoCryptoProvider
import run.koto.desktop.data.local.db.KotoDb
import run.koto.desktop.data.ws.KotoWebSocket
import run.koto.desktop.data.ws.WebSocketEventDispatcher
import run.koto.desktop.domain.model.Session
import run.koto.desktop.domain.repository.AuthRepository
import run.koto.desktop.domain.repository.ConversationRepository

/**
 * Single owner of session-driven side effects.
 *
 * On session availability (access token present) drives:
 *   1. crypto.init(accountId) — rehydrate Rust store from persisted keys.
 *   2. WebSocket connect — authenticated stream of new_message / delivered / read.
 *   3. One-shot sync of conversations so the UI reflects server state on login.
 *
 * On session loss tears the WS connection down. Run once at app startup — the
 * collector keeps the lifecycle in lockstep with [AuthRepository.session] without
 * requiring ViewModels to know anything about background bootstrap.
 */
class SessionCoordinator(
    private val auth             : AuthRepository,
    private val crypto           : KotoCryptoProvider,
    private val ws               : KotoWebSocket,
    private val wsDispatcher     : WebSocketEventDispatcher,
    private val conversationRepo : ConversationRepository,
    private val db               : KotoDb,
) {
    private val log = LoggerFactory.getLogger(SessionCoordinator::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wsConsumer  : Job? = null
    private var cleanupJob  : Job? = null

    fun start() {
        auth.session
            .map { it?.takeIf { s -> s.accessToken.isNotBlank() } }
            .distinctUntilChanged(::sameSession)
            .onEach(::applySession)
            .launchIn(scope)
        cleanupJob = scope.launch { runDisappearingCleanup() }
    }

    fun stop() {
        wsConsumer?.cancel()
        cleanupJob?.cancel()
        ws.disconnect()
        scope.coroutineContext[Job]?.cancel()
    }

    private suspend fun applySession(session: Session?) {
        if (session == null) {
            log.info("session cleared, disconnecting ws + wiping local chat data")
            ws.disconnect()
            wsConsumer?.cancel()
            wsConsumer = null
            // Plaintext wipe: zero out plaintext_cache / ciphertext before DELETE so pages
            // released to SQLite's freelist no longer carry message bodies. Depends on
            // PRAGMA secure_delete=ON set in DatabaseFactory.
            runCatching {
                db.kotoDbQueries.wipeAllMessages()
                db.kotoDbQueries.wipeAllConversations()
                db.kotoDbQueries.vacuum()
            }.onFailure { log.warn("post-signOut wipe failed", it) }
            return
        }
        log.info("session active, bootstrapping account_id={}", session.accountId)
        // Heal legacy state: clients registered before the sessions table
        // landed have no session_id stored. Force one refresh — the server's
        // RefreshTokens path mints a session row on the first call when none
        // exists for the refresh-token hash, populating session_id locally.
        if (session.sessionId.isBlank()) {
            log.info("session_id missing locally, forcing refresh to mint one")
            auth.refresh().onFailure { log.warn("legacy session_id heal refresh failed", it) }
        }
        crypto.init(session.accountId).onFailure { log.warn("crypto.init failed", it) }
        ws.connect(scope)
        wsConsumer?.cancel()
        wsConsumer = wsDispatcher.start(scope)
        scope.launch { conversationRepo.sync() }
    }

    /**
     * Periodic sweep of expired messages. Runs every minute; cheap SQL DELETE with an
     * indexed predicate so the cost is near-zero unless rows exist.
     */
    private suspend fun runDisappearingCleanup() {
        while (scope.isActive) {
            runCatching { db.kotoDbQueries.deleteExpired(System.currentTimeMillis()) }
                .onFailure { log.warn("disappearing cleanup failed", it) }
            delay(CLEANUP_INTERVAL_MS)
        }
    }

    private fun sameSession(a: Session?, b: Session?): Boolean =
        a?.accountId == b?.accountId && a?.accessToken == b?.accessToken

    companion object {
        private const val CLEANUP_INTERVAL_MS = 60_000L
    }
}
