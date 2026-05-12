package run.koto.desktop.crypto.security

import com.github.javakeyring.Keyring
import com.github.javakeyring.PasswordAccessException
import org.slf4j.LoggerFactory
import java.util.Base64

/**
 * [SecretVault] backed by the OS native credential store via java-keyring:
 *   - Windows: Credential Manager (DPAPI)
 *   - macOS:   Keychain Services
 *   - Linux:   Secret Service (libsecret) or KWallet
 *
 * java-keyring stores strings, so binary values are Base64-encoded before write
 * and decoded on read. Padding isn't stripped, so the layer round-trips byte-for-byte.
 */
class KeyringVault(
    private val service: String = DEFAULT_SERVICE,
) : SecretVault {

    private val log = LoggerFactory.getLogger(KeyringVault::class.java)

    private val keyring: Keyring? = runCatching { Keyring.create() }
        .onFailure { log.warn("OS keyring unavailable, SecretVault disabled", it) }
        .getOrNull()

    override fun isAvailable(): Boolean = keyring != null

    override fun setSecret(account: String, value: ByteArray) {
        val k = keyring ?: throw IllegalStateException("keyring not initialised")
        val encoded = Base64.getEncoder().encodeToString(value)
        try {
            k.setPassword(service, account, encoded)
        } catch (e: PasswordAccessException) {
            log.error("keyring setPassword failed account={}", account, e)
            throw e
        }
    }

    override fun getSecret(account: String): ByteArray? {
        val k = keyring ?: return null
        return try {
            Base64.getDecoder().decode(k.getPassword(service, account))
        } catch (_: PasswordAccessException) {
            null
        } catch (e: Throwable) {
            log.warn("keyring getPassword decode failed account={}", account, e)
            null
        }
    }

    override fun deleteSecret(account: String) {
        val k = keyring ?: return
        runCatching { k.deletePassword(service, account) }
            .onFailure { if (it !is PasswordAccessException) log.debug("deleteSecret failed", it) }
    }

    companion object {
        /** Constant service name so DPAPI/Keychain group all Koto secrets together. */
        const val DEFAULT_SERVICE = "run.koto.desktop"
    }
}
