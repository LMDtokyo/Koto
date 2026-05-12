package run.koto.desktop.crypto

/**
 * Signal Protocol crypto facade used by the data layer.
 *
 * Semantics mirror `run.koto.crypto.CryptoManager` in the Android module so the
 * same Rust crate (crypto/ at repo root) is the single source of truth for E2EE.
 * Identity key pair + signed prekey + Kyber prekey are persisted via [CryptoStore]
 * so sessions survive app restarts; one-time prekeys are consumed server-side and
 * are not restored after the initial generation.
 *
 * Concurrency: the live instance behind this interface is Arc<Mutex<…>> on the
 * Rust side, so callers may invoke methods in parallel safely.
 */
interface KotoCryptoProvider {

    /** True once [init] or [generateAndSaveKeys] has produced a live instance. */
    fun isInitialised(): Boolean

    /**
     * Restore crypto state for [accountId] from [CryptoStore]. No-op if the store
     * has no identity — caller must then run [generateAndSaveKeys].
     */
    suspend fun init(accountId: String): Result<Unit>

    /**
     * Generate a fresh identity + signed prekey + 100 one-time prekeys + Kyber prekey,
     * persist to [CryptoStore], construct the live crypto instance, return the bundle
     * whose public parts get uploaded to the server at registration.
     *
     * If [seedPhrase] is non-null the identity key is **deterministically** derived
     * from a BIP39 mnemonic via HKDF-SHA256 (info = "koto/identity-key/v1") — same
     * phrase always produces the same identity public key and therefore the same
     * backend `account_id`. Prekeys remain freshly random regardless to preserve
     * forward secrecy on multi-device recovery.
     */
    suspend fun generateAndSaveKeys(seedPhrase: List<String>? = null): Result<RegistrationResult>

    /**
     * Re-derive only the identity public key from a BIP39 mnemonic without
     * touching the store or generating prekeys. UI uses this to preview the
     * resolved `account_id` before the user commits to register/restore.
     */
    suspend fun previewIdentityFromSeed(seedPhrase: List<String>): Result<ByteArray>

    /**
     * Establish an X3DH + Kyber session with [peerAccountId] using [bundle] fetched
     * from GET /v1/keys/{peerAccountId}. Must succeed before [encrypt] will work.
     */
    suspend fun processPreKeyBundle(peerAccountId: String, bundle: PeerBundle): Result<Unit>

    suspend fun encrypt(peerAccountId: String, plaintext: ByteArray): Result<ByteArray>
    suspend fun decrypt(peerAccountId: String, ciphertext: ByteArray): Result<ByteArray>
}

/**
 * Output of [KotoCryptoProvider.generateAndSaveKeys]. The [accountId] is derived
 * from [identityPublicKey] (hex) — it IS the client's network address, so the
 * caller can skip computing it themselves.
 */
data class RegistrationResult(
    val accountId         : String,
    val identityPublicKey : ByteArray,
    val registrationId    : Long,
    val signedPreKeyId    : Long,
    val signedPreKeyPub   : ByteArray,
    val signedPreKeySig   : ByteArray,
    val kyberPreKeyId     : Long,
    val kyberPreKeyPub    : ByteArray,
    val kyberPreKeySig    : ByteArray,
    val oneTimePreKeys    : List<PublicPreKey>,
)

data class PublicPreKey(
    val id        : Long,
    val publicKey : ByteArray,
)

/**
 * Prekey bundle returned by the server (GET /v1/keys/{accountId}).
 * `oneTimePreKey*` are optional — server returns one unused OTPK if available.
 */
data class PeerBundle(
    val registrationId    : Long,
    val deviceId          : Long = 1,
    val identityPublicKey : ByteArray,
    val signedPreKeyId    : Long,
    val signedPreKeyPub   : ByteArray,
    val signedPreKeySig   : ByteArray,
    val kyberPreKeyId     : Long,
    val kyberPreKeyPub    : ByteArray,
    val kyberPreKeySig    : ByteArray,
    val oneTimePreKeyId   : Long?,
    val oneTimePreKeyPub  : ByteArray?,
)
