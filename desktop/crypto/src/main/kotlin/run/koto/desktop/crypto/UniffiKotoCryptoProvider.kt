package run.koto.desktop.crypto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uniffi.koto_crypto.CryptoException
import uniffi.koto_crypto.KotoCrypto
import uniffi.koto_crypto.KyberPreKeyData
import uniffi.koto_crypto.PreKeyBundleInput
import uniffi.koto_crypto.PreKeyData
import uniffi.koto_crypto.SignedPreKeyData
import uniffi.koto_crypto.generateRegistrationBundle
import uniffi.koto_crypto.generateRegistrationBundleFromSeed
import uniffi.koto_crypto.identityPublicKeyFromSeed
import java.security.SecureRandom

/**
 * Production crypto provider — delegates to the Rust libsignal crate via uniffi.
 *
 * Native library lookup: JNA scans classpath for `{platform}/koto_crypto.{dll,dylib,so}`.
 * Windows : win32-x86-64/koto_crypto.dll
 * macOS   : darwin/libkoto_crypto.dylib
 * Linux   : linux-x86-64/libkoto_crypto.so
 * The files ship as resources inside the :crypto jar.
 */
class UniffiKotoCryptoProvider(
    private val store: CryptoStore,
) : KotoCryptoProvider {

    @Volatile private var crypto: KotoCrypto? = null
    private val mutex = Mutex()

    override fun isInitialised(): Boolean = crypto != null

    override suspend fun init(accountId: String): Result<Unit> = runCatching {
        if (crypto != null) return@runCatching
        mutex.withLock {
            if (crypto != null) return@withLock
            withContext(Dispatchers.IO) {
                val identity = store.loadIdentity() ?: return@withContext
                val inst = KotoCrypto(
                    identity.identityKeyPair,
                    identity.registrationId.toUInt(),
                    accountId,
                )
                store.loadSignedPrekey()?.let { spk ->
                    inst.loadSignedPrekeys(listOf(SignedPreKeyData(
                        id         = spk.id.toUInt(),
                        publicKey  = spk.publicKey,
                        signature  = spk.signature,
                        privateKey = spk.privateKey,
                    )))
                }
                store.loadKyberPrekey()?.let { kpk ->
                    inst.loadKyberPrekeys(listOf(KyberPreKeyData(
                        id         = kpk.id.toUInt(),
                        publicKey  = EMPTY,
                        signature  = EMPTY,
                        serialized = kpk.serialized,
                    )))
                }
                crypto = inst
            }
        }
    }

    override suspend fun generateAndSaveKeys(seedPhrase: List<String>?): Result<RegistrationResult> = runCatching {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val regId    = secureRegistrationId()
                val rust     = if (seedPhrase != null) {
                    generateRegistrationBundleFromSeed(seedPhrase, regId)
                } else {
                    generateRegistrationBundle(regId)
                }
                // Backend derives account_id from the 32-byte raw DJB body
                // (no 0x05 prefix). Match here so CryptoStore key + session
                // account_id agree across module boundaries.
                val accountId = rust.identityPublicKey.stripDjbPrefix().toHex()

                store.saveIdentity(CryptoStore.Identity(rust.identityKeyPair, regId.toLong()))
                store.saveSignedPrekey(CryptoStore.SignedPrekey(
                    id         = rust.signedPrekey.id.toLong(),
                    publicKey  = rust.signedPrekey.publicKey,
                    privateKey = rust.signedPrekey.privateKey,
                    signature  = rust.signedPrekey.signature,
                ))
                store.saveKyberPrekey(CryptoStore.KyberPrekey(
                    id         = rust.kyberPrekey.id.toLong(),
                    serialized = rust.kyberPrekey.serialized,
                ))

                val inst = KotoCrypto(rust.identityKeyPair, regId, accountId)
                inst.loadSignedPrekeys(listOf(rust.signedPrekey))
                inst.loadKyberPrekeys(listOf(rust.kyberPrekey))
                inst.loadPrekeys(rust.prekeys)
                crypto = inst

                RegistrationResult(
                    accountId         = accountId,
                    identityPublicKey = rust.identityPublicKey,
                    registrationId    = regId.toLong(),
                    signedPreKeyId    = rust.signedPrekey.id.toLong(),
                    signedPreKeyPub   = rust.signedPrekey.publicKey,
                    signedPreKeySig   = rust.signedPrekey.signature,
                    kyberPreKeyId     = rust.kyberPrekey.id.toLong(),
                    kyberPreKeyPub    = rust.kyberPrekey.publicKey,
                    kyberPreKeySig    = rust.kyberPrekey.signature,
                    oneTimePreKeys    = rust.prekeys.map { PublicPreKey(it.id.toLong(), it.publicKey) },
                )
            }
        }
    }

    override suspend fun processPreKeyBundle(
        peerAccountId: String,
        bundle: PeerBundle,
    ): Result<Unit> = runCatching {
        val inst = requireCrypto()
        withContext(Dispatchers.IO) {
            inst.processPrekeyBundle(peerAccountId, PreKeyBundleInput(
                registrationId     = bundle.registrationId.toUInt(),
                deviceId           = bundle.deviceId.toUInt(),
                identityKey        = bundle.identityPublicKey,
                signedPrekeyId     = bundle.signedPreKeyId.toUInt(),
                signedPrekeyPublic = bundle.signedPreKeyPub,
                signedPrekeySig    = bundle.signedPreKeySig,
                prekeyId           = bundle.oneTimePreKeyId?.toUInt(),
                prekeyPublic       = bundle.oneTimePreKeyPub,
                kyberPrekeyId      = bundle.kyberPreKeyId.toUInt(),
                kyberPrekeyPublic  = bundle.kyberPreKeyPub,
                kyberPrekeySig     = bundle.kyberPreKeySig,
            ))
        }
    }

    override suspend fun previewIdentityFromSeed(seedPhrase: List<String>): Result<ByteArray> =
        runCatching {
            withContext(Dispatchers.IO) { identityPublicKeyFromSeed(seedPhrase) }
        }

    override suspend fun encrypt(peerAccountId: String, plaintext: ByteArray): Result<ByteArray> =
        runCatching {
            val inst = requireCrypto()
            withContext(Dispatchers.IO) { inst.encrypt(peerAccountId, plaintext) }
        }

    override suspend fun decrypt(peerAccountId: String, ciphertext: ByteArray): Result<ByteArray> =
        runCatching {
            val inst = requireCrypto()
            withContext(Dispatchers.IO) { inst.decrypt(peerAccountId, ciphertext) }
        }

    private fun requireCrypto(): KotoCrypto = crypto
        ?: throw CryptoException.NoSession()

    companion object {
        private val EMPTY = ByteArray(0)
        private val rng = SecureRandom()

        // 31-bit unsigned id matches the Rust + Signal Protocol expectation.
        // SecureRandom avoids same-millisecond collisions that the previous
        // System.currentTimeMillis() approach allowed across devices.
        private fun secureRegistrationId(): UInt =
            (rng.nextInt(Int.MAX_VALUE) and 0x7FFFFFFF).toUInt()
    }
}

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
