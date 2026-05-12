package run.koto.desktop.crypto.security

import org.slf4j.LoggerFactory
import javax.crypto.SecretKey

/**
 * Owns the single AES-256 master key used by [LocalAead] for all at-rest secrets.
 *
 * Lifecycle:
 *   - First run — generate 32 random bytes, store in OS keystore under [KEY_ACCOUNT].
 *   - Later runs — load the bytes back and rebuild the [SecretKey].
 *
 * The key never hits disk in plaintext — only the OS keystore sees it. If that store
 * is wiped (user logout, credential reset) the client's local secrets become
 * undecryptable and the user re-logs in, rebuilding both tokens and Signal keys.
 */
class MasterKeyManager(private val vault: SecretVault) {

    private val log = LoggerFactory.getLogger(MasterKeyManager::class.java)

    @Volatile private var cached: SecretKey? = null

    fun getOrCreate(): SecretKey {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val existing = vault.getSecret(KEY_ACCOUNT)
            val bytes = if (existing != null && existing.size == KEY_SIZE) {
                existing
            } else {
                val fresh = LocalAead.generateKeyBytes()
                vault.setSecret(KEY_ACCOUNT, fresh)
                log.info("master AES key generated and stored in {}", vault::class.simpleName)
                fresh
            }
            val key = LocalAead.keyFrom(bytes)
            cached = key
            return key
        }
    }

    fun reset() {
        vault.deleteSecret(KEY_ACCOUNT)
        cached = null
    }

    companion object {
        private const val KEY_ACCOUNT = "master-key-v1"
        private const val KEY_SIZE    = 32
    }
}
