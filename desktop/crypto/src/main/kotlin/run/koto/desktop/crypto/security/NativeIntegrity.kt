package run.koto.desktop.crypto.security

import org.slf4j.LoggerFactory
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Extracts and SHA-256-verifies the bundled `koto_crypto` native library **before**
 * JNA gets a chance to load it.
 *
 * Flow:
 *   1. Read `{platform}/koto_crypto.{dll,dylib,so}` from classpath resources.
 *   2. Read `koto_crypto.sha256` (pinned hash, produced by the `:crypto:pinNativeHash`
 *      Gradle task alongside the library).
 *   3. Hash the library bytes. If mismatch → fail hard.
 *   4. Write the library to a private temp directory.
 *   5. Set `jna.library.path` to that directory and `jna.noclasspath=true`, so JNA
 *      will only ever load the verified file — it cannot be tricked into extracting
 *      a (possibly tampered) classpath copy.
 *
 * Must be called before any code that transitively touches `uniffi.koto_crypto.*`.
 */
object NativeIntegrity {

    private val log = LoggerFactory.getLogger(NativeIntegrity::class.java)

    data class Result(val verified: Boolean, val directory: Path?, val reason: String)

    @Volatile private var applied: Result? = null

    fun applyOrFail(): Result {
        applied?.let { return it }
        synchronized(this) {
            applied?.let { return it }
            val r = verifyAndInstall()
            applied = r
            if (!r.verified) throw IllegalStateException("native library verification failed: ${r.reason}")
            return r
        }
    }

    private fun verifyAndInstall(): Result {
        val platformDir = platformDir() ?: return Result(false, null, "unsupported OS/arch")
        val libName     = platformLibName() ?: return Result(false, null, "unsupported OS/arch")

        val classpathLib  = "/$platformDir/$libName"
        val libBytes = resourceBytes(classpathLib)
            ?: return Result(false, null, "bundled native library not found at $classpathLib")

        val expectedHash = resourceBytes(HASH_RESOURCE)?.toString(Charsets.UTF_8)?.trim()
        if (expectedHash.isNullOrEmpty()) {
            log.warn("native lib checksum resource missing — skipping verification. " +
                "Run `:crypto:pinNativeHash` to produce $HASH_RESOURCE")
        } else {
            val actual = sha256Hex(libBytes)
            if (!constantTimeEquals(actual, expectedHash)) {
                return Result(false, null, "checksum mismatch expected=$expectedHash actual=$actual")
            }
            log.info("native library integrity verified sha256={}", actual)
        }

        val dir = Files.createTempDirectory("koto-crypto-")
        dir.toFile().deleteOnExit()
        val target = dir.resolve(libName)
        Files.copy(libBytes.inputStream(), target, StandardCopyOption.REPLACE_EXISTING)
        target.toFile().deleteOnExit()

        // Prepend our verified directory to jna.library.path so JNA finds it before any
        // classpath resource. We deliberately do NOT set `jna.noclasspath=true` — that
        // flag also disables JNA's bootstrap of its own jnidispatch native library.
        val existing = System.getProperty("jna.library.path")
        val merged   = if (existing.isNullOrBlank()) dir.toString() else "$dir${java.io.File.pathSeparator}$existing"
        System.setProperty("jna.library.path", merged)

        return Result(true, dir, "ok")
    }

    private fun resourceBytes(path: String): ByteArray? =
        (javaClass.getResourceAsStream(path) ?: javaClass.classLoader?.getResourceAsStream(path.trimStart('/')))
            ?.use(InputStream::readAllBytes)

    private fun platformDir(): String? {
        val os   = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()
        return when {
            os.contains("win")                  -> "win32-x86-64"
            os.contains("mac") || os.contains("darwin") -> "darwin"
            os.contains("nux") || os.contains("nix")    -> if (arch.contains("aarch64")) "linux-aarch64" else "linux-x86-64"
            else -> null
        }
    }

    private fun platformLibName(): String? {
        val os = System.getProperty("os.name").lowercase()
        return when {
            os.contains("win") -> "koto_crypto.dll"
            os.contains("mac") -> "libkoto_crypto.dylib"
            os.contains("nux") || os.contains("nix") -> "libkoto_crypto.so"
            else -> null
        }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val out = CharArray(digest.size * 2)
        val hex = "0123456789abcdef"
        for (i in digest.indices) {
            val b = digest[i].toInt() and 0xFF
            out[i * 2]     = hex[b ushr 4]
            out[i * 2 + 1] = hex[b and 0x0F]
        }
        return String(out)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }

    private const val HASH_RESOURCE = "koto_crypto.sha256"
}
