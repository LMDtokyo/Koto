package run.koto.desktop.data.local

import org.slf4j.LoggerFactory
import run.koto.desktop.crypto.security.LocalAead
import run.koto.desktop.data.local.db.KotoDb

/**
 * Persists the user's BIP39 recovery phrase encrypted at rest. The plaintext
 * never touches disk — it is AES-GCM-sealed by [LocalAead] (whose key lives
 * in the OS keystore: Windows DPAPI / macOS Keychain / Linux libsecret).
 *
 * Single-row table; the phrase is rewritten at every register/restore so a
 * fresh ciphertext is produced each time (different nonce ⇒ different bytes).
 */
class SeedStore(
    private val db   : KotoDb,
    private val aead : LocalAead,
) {
    private val log = LoggerFactory.getLogger(SeedStore::class.java)

    /** Returns the phrase as a list of words, or null when none stored / un-decryptable. */
    fun read(): List<String>? {
        val row = db.kotoDbQueries.getSeedPhrase().executeAsOneOrNull() ?: return null
        return try {
            val plain = aead.decrypt(row.hexToBytes()).toString(Charsets.UTF_8)
            plain.split(' ').filter { it.isNotBlank() }
        } catch (e: Throwable) {
            log.warn("seed decrypt failed, dropping row", e)
            clear()
            null
        }
    }

    fun write(phrase: List<String>) {
        val cleaned = phrase.filter { it.isNotBlank() }
        if (cleaned.isEmpty()) return
        val ciphertext = aead.encrypt(cleaned.joinToString(" ").toByteArray(Charsets.UTF_8)).toHex()
        db.kotoDbQueries.upsertSeedPhrase(ciphertext)
    }

    fun clear() { db.kotoDbQueries.clearSeedPhrase() }
}

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

private fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "hex string odd length" }
    val out = ByteArray(length / 2)
    for (i in out.indices) {
        out[i] = ((Character.digit(this[2 * i], 16) shl 4) or
                  Character.digit(this[2 * i + 1], 16)).toByte()
    }
    return out
}
