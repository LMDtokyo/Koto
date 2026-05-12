package run.koto.desktop.crypto

/**
 * Fallback binding used when the Rust `koto_crypto` native library cannot be
 * loaded (missing resource, wrong architecture). Every method fails loudly so
 * the UI surfaces "encryption unavailable" instead of silently falling back to
 * plaintext — matching the Phase 05.1 security policy.
 *
 * Not installed by default; [UniffiKotoCryptoProvider] is the production binding.
 * Swap via Koin only when verifying UI behaviour without native libs.
 */
class NotLinkedCryptoProvider : KotoCryptoProvider {

    private val error = IllegalStateException(
        "libsignal native library not linked — crypto operations are disabled.",
    )

    override fun isInitialised(): Boolean = false

    override suspend fun init(accountId: String): Result<Unit> = Result.failure(error)

    override suspend fun generateAndSaveKeys(seedPhrase: List<String>?): Result<RegistrationResult> = Result.failure(error)

    override suspend fun previewIdentityFromSeed(seedPhrase: List<String>): Result<ByteArray> = Result.failure(error)

    override suspend fun processPreKeyBundle(
        peerAccountId: String,
        bundle: PeerBundle,
    ): Result<Unit> = Result.failure(error)

    override suspend fun encrypt(
        peerAccountId: String,
        plaintext: ByteArray,
    ): Result<ByteArray> = Result.failure(error)

    override suspend fun decrypt(
        peerAccountId: String,
        ciphertext: ByteArray,
    ): Result<ByteArray> = Result.failure(error)
}
