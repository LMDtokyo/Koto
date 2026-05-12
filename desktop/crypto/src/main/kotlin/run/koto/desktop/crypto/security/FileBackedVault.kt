package run.koto.desktop.crypto.security

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFilePermissions
import java.util.Base64
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

/**
 * Fallback [SecretVault] when the OS keystore is unavailable (headless CI, minimal
 * Linux without libsecret, etc). Writes a Properties file with restrictive ACLs:
 *
 *   - POSIX: chmod 0600
 *   - Windows: ACL stripped to current user only
 *
 * **Security note:** this is weaker than the OS keystore — a compromised user
 * process on the same machine can read the file. [KeyringVault] should be preferred.
 * This backend exists so the app does not fall back to plaintext-in-SQLite just
 * because the user's environment lacks a secret service.
 */
class FileBackedVault(
    private val path: Path,
) : SecretVault {

    private val log = LoggerFactory.getLogger(FileBackedVault::class.java)

    private val cache = ConcurrentHashMap<String, ByteArray>()

    init {
        Files.createDirectories(path.parent)
        if (!Files.exists(path)) {
            Files.createFile(path)
            applyRestrictivePermissions(path)
        }
        load()
    }

    override fun isAvailable(): Boolean = true

    override fun setSecret(account: String, value: ByteArray) {
        cache[account] = value
        persist()
    }

    override fun getSecret(account: String): ByteArray? = cache[account]?.copyOf()

    override fun deleteSecret(account: String) {
        if (cache.remove(account) != null) persist()
    }

    private fun load() {
        val props = Properties()
        Files.newInputStream(path).use { props.load(it) }
        props.forEach { k, v -> cache[k.toString()] = Base64.getDecoder().decode(v.toString()) }
    }

    private fun persist() {
        val props = Properties()
        cache.forEach { (k, v) -> props.setProperty(k, Base64.getEncoder().encodeToString(v)) }
        val tmp = path.resolveSibling(path.fileName.toString() + ".tmp")
        Files.newOutputStream(tmp).use { props.store(it, null) }
        applyRestrictivePermissions(tmp)
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun applyRestrictivePermissions(target: Path) {
        runCatching {
            Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rw-------"))
        }.onFailure {
            // Non-POSIX filesystem — try Windows ACL
            runCatching {
                val view = Files.getFileAttributeView(target, AclFileAttributeView::class.java) ?: return@runCatching
                val owner = Files.getOwner(target)
                val allow = AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(owner)
                    .setPermissions(
                        AclEntryPermission.READ_DATA,
                        AclEntryPermission.WRITE_DATA,
                        AclEntryPermission.APPEND_DATA,
                        AclEntryPermission.READ_ATTRIBUTES,
                        AclEntryPermission.WRITE_ATTRIBUTES,
                        AclEntryPermission.READ_ACL,
                        AclEntryPermission.SYNCHRONIZE,
                        AclEntryPermission.DELETE,
                    )
                    .build()
                view.acl = listOf(allow)
            }.onFailure { log.debug("could not tighten permissions on {}", target, it) }
        }
    }
}
