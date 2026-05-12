package run.koto.desktop.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import run.koto.desktop.crypto.KotoCryptoProvider
import run.koto.desktop.data.local.SeedStore
import run.koto.desktop.data.local.SessionStore
import run.koto.desktop.data.remote.api.AuthApi
import run.koto.desktop.data.remote.api.SessionsApi
import run.koto.desktop.data.remote.api.UserApi
import run.koto.desktop.data.remote.dto.KyberPrekeyPayload
import run.koto.desktop.data.remote.dto.OneTimePrekeyPayload
import run.koto.desktop.data.remote.dto.RegisterRequest
import run.koto.desktop.data.remote.dto.SignedPrekeyPayload
import run.koto.desktop.data.remote.dto.UploadKeysRequest
import run.koto.desktop.domain.DomainError
import run.koto.desktop.domain.model.Account
import run.koto.desktop.domain.model.LinkedDevice
import run.koto.desktop.domain.model.Session
import run.koto.desktop.domain.repository.AuthRepository
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.Base64

/**
 * Two-step registration:
 *   1. POST /v1/auth/register — HEX identity + signed prekey + OTPK (auth service legacy shape,
 *      no Kyber). Receives token pair.
 *   2. PUT /v1/keys — BASE64 full PQXDH bundle including Kyber1024 prekey. Needs the token from
 *      step 1, so it goes through the main Bearer-plugin client.
 *
 * Refresh is single-flighted via [refreshLock] so concurrent 401s do not stampede the server.
 */
/**
 * The `userApiProvider` is a deferred lookup rather than a direct injection — otherwise
 * constructor resolution loops: `AuthRepository → UserApi → mainHttpClient → AuthRepository`.
 * The lambda is only invoked inside [register], by which point the main client has been
 * fully constructed (it already has access to this very [AuthRepository] via the Koin graph).
 */
class AuthRepositoryImpl(
    private val authApi             : AuthApi,
    private val userApiProvider     : () -> UserApi,
    private val sessionsApiProvider : () -> SessionsApi,
    private val crypto              : KotoCryptoProvider,
    private val store               : SessionStore,
    private val seedStore           : SeedStore,
) : AuthRepository {

    private val log = LoggerFactory.getLogger(AuthRepositoryImpl::class.java)

    private val state = MutableStateFlow<Session?>(null)
    override val session: Flow<Session?> = state.asStateFlow()

    private val refreshLock = Mutex()

    init {
        state.value = store.read()
    }

    override suspend fun register(displayName: String, seedPhrase: List<String>): Result<Account> =
        runCatching { provisionAccount(displayName, seedPhrase, restore = false) }
            .onFailure { log.error("register failed", it) }

    override suspend fun restore(seedPhrase: List<String>): Result<Account> =
        runCatching { provisionAccount(displayName = "", seedPhrase = seedPhrase, restore = true) }
            .onFailure { log.error("restore failed", it) }

    private suspend fun provisionAccount(displayName: String, seedPhrase: List<String>, restore: Boolean): Account {
        val bundle = crypto.generateAndSaveKeys(seedPhrase).getOrElse {
            log.error("crypto keygen failed", it)
            throw DomainError.Internal("crypto unavailable", it)
        }

        // Auth service expects raw 32-byte DJB bodies. libsignal serializes
        // Curve25519 pubkeys as 33 bytes (0x05 || body); strip the type byte.
        // Full 33-byte form is preserved for PUT /v1/keys + peer bundles.
        val registerReq = RegisterRequest(
            identity_key       = bundle.identityPublicKey.stripDjbPrefix().toHex(),
            signed_pre_key     = bundle.signedPreKeyPub.stripDjbPrefix().toHex(),
            signed_pre_key_sig = bundle.signedPreKeySig.toHex(),
            signed_pre_key_id  = bundle.signedPreKeyId.toInt(),
            one_time_pre_keys  = bundle.oneTimePreKeys.map { it.publicKey.stripDjbPrefix().toHex() },
        )

        val pair = withContext(Dispatchers.IO) {
            if (restore) authApi.restore(registerReq) else authApi.register(registerReq)
        }
        val next = Session(pair.access_token, pair.refresh_token, pair.account_id, pair.session_id, pair.expires_at)
        state.value = next
        store.write(next)
        // Persist the recovery phrase encrypted at rest so Settings → Фраза
        // восстановления can re-display it. We rewrite on every register/restore
        // — same words, fresh nonce, fresh ciphertext.
        seedStore.write(seedPhrase)
        log.info("session bootstrapped account_id={}", pair.account_id)

        val userApi = userApiProvider()

        // PQXDH bundle (base64, includes Kyber). Non-fatal — top-up job retries.
        val uploadReq = UploadKeysRequest(
            identity_key     = bundle.identityPublicKey.toB64(),
            registration_id  = bundle.registrationId.toInt(),
            signed_prekey    = SignedPrekeyPayload(
                id         = bundle.signedPreKeyId.toInt(),
                public_key = bundle.signedPreKeyPub.toB64(),
                signature  = bundle.signedPreKeySig.toB64(),
            ),
            kyber_prekey     = KyberPrekeyPayload(
                id         = bundle.kyberPreKeyId.toInt(),
                public_key = bundle.kyberPreKeyPub.toB64(),
                signature  = bundle.kyberPreKeySig.toB64(),
            ),
            one_time_prekeys = bundle.oneTimePreKeys.map {
                OneTimePrekeyPayload(id = it.id.toInt(), public_key = it.publicKey.toB64())
            },
        )
        runCatching { withContext(Dispatchers.IO) { userApi.uploadKeys(uploadReq) } }
            .onFailure { log.warn("PQXDH bundle upload failed, will retry on next sync", it) }

        if (displayName.isNotBlank()) {
            runCatching { withContext(Dispatchers.IO) { userApi.updateProfile(displayName = displayName) } }
                .onFailure { log.warn("profile display_name update failed", it) }
        }

        return Account(
            accountId   = pair.account_id,
            displayName = displayName,
            avatarUrl   = null,
            publicKey   = bundle.identityPublicKey,
        )
    }

    override suspend fun refresh(): Result<Session> = refreshLock.withLock {
        val current = state.value
            ?: return@withLock Result.failure(DomainError.Unauthorized("no session"))
        runCatching {
            val pair = withContext(Dispatchers.IO) { authApi.refresh(current.refreshToken) }
            val next = Session(pair.access_token, pair.refresh_token, pair.account_id, pair.session_id, pair.expires_at)
            state.value = next
            store.write(next)
            log.debug("refreshed tokens account_id={}", pair.account_id)
            next
        }.onFailure { e ->
            if (e is DomainError.Unauthorized) {
                // Refresh token was revoked / expired server-side. Drop the session
                // so the UI bounces the user back to auth instead of looping with a
                // dead token.
                log.info("refresh rejected by server, clearing local session")
                state.value = null
                store.clear()
                seedStore.clear()
            } else {
                log.warn("refresh failed transiently, session preserved", e)
            }
        }
    }

    override suspend fun signOut() {
        val current = state.value ?: return
        runCatching { withContext(Dispatchers.IO) { authApi.revoke(current.refreshToken) } }
            .onFailure { log.debug("revoke call failed, clearing local session anyway", it) }
        state.value = null
        store.clear()
        seedStore.clear()
        // Plaintext wipe + VACUUM happens in SessionCoordinator when it observes the
        // session drop — it owns the DB handle and the cleanup scheduler.
        log.info("signed out")
    }

    override suspend fun previewKotoId(seedPhrase: List<String>): Result<String> =
        crypto.previewIdentityFromSeed(seedPhrase).map { it.stripDjbPrefix().toHex() }

    override suspend fun currentAccountId(): String? = state.value?.accountId

    override suspend fun listDevices(): Result<List<LinkedDevice>> = runCatching {
        withContext(Dispatchers.IO) { sessionsApiProvider().list() }
            .sessions
            .map { dto ->
                LinkedDevice(
                    id         = dto.id,
                    deviceName = dto.device_name,
                    platform   = dto.platform,
                    appVersion = dto.app_version,
                    clientIp   = dto.client_ip,
                    createdAt  = parseIsoMillis(dto.created_at),
                    lastSeenAt = parseIsoMillis(dto.last_seen_at),
                )
            }
    }.onFailure { log.warn("list devices failed", it) }

    override suspend fun revokeDevice(sessionId: String): Result<Unit> = runCatching {
        // Revoking the *current* device is a soft sign-out: the server drops
        // the session row, but our local state needs the same teardown as a
        // normal logout otherwise we keep replaying a dead refresh token.
        val isSelf = state.value?.sessionId == sessionId
        withContext(Dispatchers.IO) { sessionsApiProvider().revoke(sessionId) }
        if (isSelf) {
            state.value = null
            store.clear()
        }
    }.onFailure { log.warn("revoke device failed", it) }
}

private fun parseIsoMillis(s: String): Long = try {
    OffsetDateTime.parse(s).toInstant().toEpochMilli()
} catch (_: DateTimeParseException) {
    0L
}

private fun ByteArray.toB64(): String = Base64.getEncoder().encodeToString(this)

private fun ByteArray.stripDjbPrefix(): ByteArray =
    if (size == 33 && this[0] == 0x05.toByte()) copyOfRange(1, size) else this

private fun ByteArray.toHex(): String {
    val hex = "0123456789abcdef"
    val out = CharArray(size * 2)
    for (i in indices) {
        val b = this[i].toInt() and 0xFF
        out[i * 2]     = hex[b ushr 4]
        out[i * 2 + 1] = hex[b and 0x0F]
    }
    return String(out)
}
