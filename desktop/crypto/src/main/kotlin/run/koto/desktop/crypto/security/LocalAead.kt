package run.koto.desktop.crypto.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Authenticated symmetric encryption for at-rest secrets (tokens, key material, plaintext cache).
 *
 * Format: `IV(12) || CIPHERTEXT || TAG(16)` — a flat byte array. The Cipher writes the tag
 * into the trailing 16 bytes of ciphertext automatically, so callers just persist / read
 * one blob per secret.
 *
 * Algorithm: AES-256/GCM/NoPadding, 96-bit random IV (NIST SP 800-38D §8.2.1), 128-bit
 * authentication tag. Re-using IVs with the same key is catastrophic for GCM, so each
 * encrypt generates a fresh IV via [SecureRandom].
 */
class LocalAead(private val key: SecretKey) {

    private val rng = SecureRandom()

    init {
        require(key.algorithm == "AES") { "AES key required, got ${key.algorithm}" }
        require(key.encoded.size == KEY_SIZE_BYTES) {
            "AES-256 key required (${KEY_SIZE_BYTES} bytes), got ${key.encoded.size}"
        }
    }

    fun encrypt(plaintext: ByteArray): ByteArray {
        val iv = ByteArray(IV_SIZE_BYTES).also(rng::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_SIZE_BITS, iv))
        val ct = cipher.doFinal(plaintext)
        return ByteArray(iv.size + ct.size).also {
            System.arraycopy(iv, 0, it, 0,        iv.size)
            System.arraycopy(ct, 0, it, iv.size,  ct.size)
        }
    }

    fun decrypt(blob: ByteArray): ByteArray {
        require(blob.size > IV_SIZE_BYTES + TAG_SIZE_BYTES) { "ciphertext blob too short" }
        val iv = blob.copyOfRange(0, IV_SIZE_BYTES)
        val ct = blob.copyOfRange(IV_SIZE_BYTES, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE_BITS, iv))
        return cipher.doFinal(ct)
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE_BYTES  = 12
        private const val TAG_SIZE_BITS  = 128
        private const val TAG_SIZE_BYTES = TAG_SIZE_BITS / 8
        private const val KEY_SIZE_BYTES = 32

        /** Wraps raw 32-byte key material in a JCE [SecretKey]. */
        fun keyFrom(bytes: ByteArray): SecretKey {
            require(bytes.size == KEY_SIZE_BYTES) { "AES-256 key must be $KEY_SIZE_BYTES bytes" }
            return SecretKeySpec(bytes, "AES")
        }

        fun generateKeyBytes(): ByteArray =
            ByteArray(KEY_SIZE_BYTES).also { SecureRandom().nextBytes(it) }
    }
}
