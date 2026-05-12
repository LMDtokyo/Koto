package run.koto.desktop.crypto.security

/**
 * Platform-native secret storage.
 *
 * Implementations wrap Windows Credential Manager (DPAPI), macOS Keychain Services,
 * or Linux Secret Service (libsecret) — the user's OS gatekeeping is the ultimate
 * line of defence. No secret material hits disk unencrypted.
 *
 * Secrets are addressed by (service, account) tuples per the convention of every
 * major OS keystore; [service] is shared across the app, [account] varies per key.
 */
interface SecretVault {

    /** True if the backing store is usable. File-backed fallbacks return true even when headless. */
    fun isAvailable(): Boolean

    /** Upsert a secret. Overwrites any existing value under the same account. */
    fun setSecret(account: String, value: ByteArray)

    /** Returns null if the account is absent. */
    fun getSecret(account: String): ByteArray?

    /** No-op if absent. */
    fun deleteSecret(account: String)
}
