package run.koto.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

data class IdentityKeys(
    val publicKey  : ByteArray,   // Ed25519 public key (32 bytes)
    val privateKey : ByteArray,   // Ed25519 private key (32 bytes, AES-GCM wrapped in Keystore)
) {
    val accountId: String get() = publicKey.toHex()
}

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
fun String.fromHex(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

@Singleton
class KeyManager @Inject constructor() {

    companion object {
        private const val KEYSTORE_ALIAS    = "koto_wrap_key"
        private const val ANDROID_KEYSTORE  = "AndroidKeyStore"
        private const val AES_GCM           = "AES/GCM/NoPadding"
        private const val GCM_TAG_LEN       = 128
        private const val GCM_IV_LEN        = 12
    }

    // ── Generate a new Ed25519 identity keypair ──────────────────────────────

    fun generateIdentityKey(): IdentityKeys {
        val rng = SecureRandom()
        val generator = Ed25519KeyPairGenerator()
        generator.init(Ed25519KeyGenerationParameters(rng))
        val pair = generator.generateKeyPair()

        val pub  = (pair.public  as Ed25519PublicKeyParameters).encoded   // 32 bytes
        val priv = (pair.private as Ed25519PrivateKeyParameters).encoded  // 32 bytes

        return IdentityKeys(publicKey = pub, privateKey = priv)
    }

    // ── Sign arbitrary data with the Ed25519 private key ─────────────────────

    fun sign(privateKeyBytes: ByteArray, data: ByteArray): ByteArray {
        val signer = org.bouncycastle.crypto.signers.Ed25519Signer()
        signer.init(true, Ed25519PrivateKeyParameters(privateKeyBytes, 0))
        signer.update(data, 0, data.size)
        return signer.generateSignature()
    }

    // ── Wrap / unwrap private key bytes using Android Keystore AES-GCM ──────

    fun wrapPrivateKey(rawPrivKey: ByteArray): ByteArray {
        ensureWrappingKey()
        val ks  = KeyStore.getInstance(ANDROID_KEYSTORE).also { it.load(null) }
        val key = ks.getKey(KEYSTORE_ALIAS, null) as javax.crypto.SecretKey
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv         = cipher.iv                          // 12 bytes (auto-generated)
        val ciphertext = cipher.doFinal(rawPrivKey)         // encrypted private key
        return iv + ciphertext                              // iv || ciphertext
    }

    fun unwrapPrivateKey(wrapped: ByteArray): ByteArray {
        val ks  = KeyStore.getInstance(ANDROID_KEYSTORE).also { it.load(null) }
        val key = ks.getKey(KEYSTORE_ALIAS, null) as javax.crypto.SecretKey
        val iv         = wrapped.copyOfRange(0, GCM_IV_LEN)
        val ciphertext = wrapped.copyOfRange(GCM_IV_LEN, wrapped.size)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LEN, iv))
        return cipher.doFinal(ciphertext)
    }

    // ── Ensure AES-256-GCM wrapping key exists in Keystore ──────────────────

    private fun ensureWrappingKey() {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).also { it.load(null) }
        if (ks.containsAlias(KEYSTORE_ALIAS)) return

        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        kg.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false) // screen-lock binding handled at UI layer
                .build()
        )
        kg.generateKey()
    }
}
