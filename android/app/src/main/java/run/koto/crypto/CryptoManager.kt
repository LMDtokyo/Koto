package run.koto.crypto

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import run.koto.data.prefs.AccountPrefs
import uniffi.koto_crypto.KotoCrypto
import uniffi.koto_crypto.KyberPreKeyData
import uniffi.koto_crypto.PreKeyBundleInput
import uniffi.koto_crypto.RegistrationBundle
import uniffi.koto_crypto.SignedPreKeyData
import uniffi.koto_crypto.generateRegistrationBundle
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real Signal Protocol wrapper (uniffi bindings → Rust libsignal-protocol).
 *
 * Responsibilities:
 *   - Generate / restore identity keys (persisted via [AccountPrefs])
 *   - Hold the live [KotoCrypto] instance (lazy init after auth)
 *   - Expose suspend functions safe to call from coroutines (dispatches to IO)
 *
 * Thread safety: the Rust side is Arc<Mutex<…>>. We just dispatch to IO
 * so heavy crypto doesn't block the main thread.
 */
@Singleton
class CryptoManager @Inject constructor(
    private val accountPrefs: AccountPrefs,
) {
    @Volatile private var crypto: KotoCrypto? = null

    /** True once init() or generateAndSaveKeys() has produced a live instance. */
    fun isInitialised(): Boolean = crypto != null

    /**
     * Restore crypto state for the given account after login/app restart.
     * No-op if identity is missing — caller must then run [generateAndSaveKeys].
     */
    suspend fun init(accountId: String) = withContext(Dispatchers.IO) {
        if (crypto != null) return@withContext

        val identityBytes  = accountPrefs.getIdentityKeyPair() ?: return@withContext
        val registrationId = accountPrefs.getCryptoRegistrationId() ?: return@withContext

        val instance = KotoCrypto(identityBytes, registrationId.toUInt(), accountId)

        // Restore signed prekey (needed to decrypt incoming PreKeySignalMessages)
        val spkId      = accountPrefs.getSignedPrekeyId()
        val spkPublic  = accountPrefs.getSignedPrekeyPublic()
        val spkPrivate = accountPrefs.getSignedPrekeyPrivate()
        val spkSig     = accountPrefs.getSignedPrekeySig()
        if (spkId != null && spkPublic != null && spkPrivate != null && spkSig != null) {
            instance.loadSignedPrekeys(listOf(SignedPreKeyData(
                id         = spkId.toUInt(),
                publicKey  = spkPublic,
                signature  = spkSig,
                privateKey = spkPrivate,
            )))
        }

        // Restore Kyber prekey (PQXDH)
        val kyberId     = accountPrefs.getKyberPrekeyId()
        val kyberSerial = accountPrefs.getKyberPrekeySerialised()
        if (kyberId != null && kyberSerial != null) {
            instance.loadKyberPrekeys(listOf(KyberPreKeyData(
                id         = kyberId.toUInt(),
                publicKey  = byteArrayOf(),   // not needed — serialized has everything
                signature  = byteArrayOf(),
                serialized = kyberSerial,
            )))
        }

        crypto = instance
    }

    /**
     * Generate fresh Signal Protocol keys, persist them, return the bundle
     * so the caller can upload public parts to the key server.
     *
     * Call ONCE at account registration — overwrites existing keys.
     */
    suspend fun generateAndSaveKeys(accountId: String): RegistrationBundle =
        withContext(Dispatchers.IO) {
            val registrationId = (System.currentTimeMillis() and 0x7FFF_FFFF).toUInt()
            val bundle = generateRegistrationBundle(registrationId)

            // Persist identity + registration ID
            accountPrefs.saveIdentityKeyPair(bundle.identityKeyPair)
            accountPrefs.saveCryptoRegistrationId(registrationId.toLong())

            // Persist signed prekey (needed to decrypt incoming messages)
            val spk = bundle.signedPrekey
            accountPrefs.saveSignedPrekey(
                id          = spk.id.toLong(),
                public_key  = spk.publicKey,
                private_key = spk.privateKey,
                signature   = spk.signature,
            )

            // Persist Kyber prekey (PQXDH)
            val kyber = bundle.kyberPrekey
            accountPrefs.saveKyberPrekey(
                id         = kyber.id.toLong(),
                serialized = kyber.serialized,
            )

            val instance = KotoCrypto(bundle.identityKeyPair, registrationId, accountId)

            // Load the just-generated prekeys into the live store
            instance.loadSignedPrekeys(listOf(SignedPreKeyData(
                id         = spk.id,
                publicKey  = spk.publicKey,
                signature  = spk.signature,
                privateKey = spk.privateKey,
            )))
            instance.loadKyberPrekeys(listOf(KyberPreKeyData(
                id         = kyber.id,
                publicKey  = kyber.publicKey,
                signature  = kyber.signature,
                serialized = kyber.serialized,
            )))

            crypto = instance
            bundle
        }

    // ── Session establishment ─────────────────────────────────────────────────

    suspend fun processPreKeyBundle(
        theirAccountId: String,
        bundle: PreKeyBundleInput,
    ) = withContext(Dispatchers.IO) {
        requireCrypto().processPrekeyBundle(theirAccountId, bundle)
    }

    // ── Encrypt / Decrypt ─────────────────────────────────────────────────────

    /** Encrypt [plaintext] for [theirAccountId]. Returns Base64 ciphertext. */
    suspend fun encrypt(theirAccountId: String, plaintext: String): String =
        withContext(Dispatchers.IO) {
            val cipher = requireCrypto().encrypt(
                theirAccountId,
                plaintext.toByteArray(Charsets.UTF_8),
            )
            Base64.encodeToString(cipher, Base64.NO_WRAP)
        }

    /** Decrypt a Base64 ciphertext. Returns null on failure (e.g. no session yet). */
    suspend fun decrypt(theirAccountId: String, ciphertextBase64: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val cipher = Base64.decode(ciphertextBase64, Base64.NO_WRAP)
                val plain  = requireCrypto().decrypt(theirAccountId, cipher)
                String(plain, Charsets.UTF_8)
            }.getOrNull()
        }

    private fun requireCrypto(): KotoCrypto =
        crypto ?: error("CryptoManager not initialised — call init() or generateAndSaveKeys() first")
}
