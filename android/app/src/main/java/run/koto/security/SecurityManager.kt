package run.koto.security

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.os.Debug
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SecurityManager — runtime integrity checks.
 *
 * Responsibilities:
 *   - Detect rooted devices (su binary, Magisk, common root apps)
 *   - Detect active debugger (Java + native)
 *   - Detect reverse engineering tools (Frida, Xposed, LSPosed)
 *   - Verify APK signature matches expected value (tamper detection)
 *
 * Returns a [SecurityStatus] that the UI can surface as a warning or
 * that sensitive operations (key export, account delete) can gate on.
 *
 * NOTE: these checks are defence-in-depth. A determined attacker with
 * full device control can bypass any single check, so the real protection
 * is: (1) server-side verification of everything, (2) E2EE so even with
 * a compromised device the server can't leak plaintext.
 */
@Singleton
class SecurityManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class SecurityStatus(
        val isRooted          : Boolean = false,
        val isDebuggerAttached: Boolean = false,
        val hasRETools        : Boolean = false,
        val isTampered        : Boolean = false,
    ) {
        val isCompromised: Boolean
            get() = isRooted || isDebuggerAttached || hasRETools || isTampered
    }

    private val _status = MutableStateFlow(SecurityStatus())
    val status: StateFlow<SecurityStatus> = _status

    /** Run all checks. Call on app start. */
    fun check() {
        _status.value = SecurityStatus(
            isRooted           = detectRoot(),
            isDebuggerAttached = detectDebugger(),
            hasRETools         = detectREtools(),
            isTampered         = detectTamper(),
        )
    }

    // ── Root detection ────────────────────────────────────────────────────────

    private fun detectRoot(): Boolean {
        // 1. Look for su binary in common locations
        val suPaths = arrayOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/system/su", "/system/bin/.ext/.su", "/vendor/bin/su",
            "/data/local/xbin/su", "/data/local/bin/su",
            "/data/local/su", "/su/bin/su",
        )
        if (suPaths.any { File(it).exists() }) return true

        // 2. Look for build tags / test-keys
        val buildTags = Build.TAGS
        if (buildTags != null && buildTags.contains("test-keys")) return true

        // 3. Common root-manager packages
        val rootApps = arrayOf(
            "com.topjohnwu.magisk",           // Magisk
            "eu.chainfire.supersu",            // SuperSU
            "com.koushikdutta.superuser",      // Superuser (Koush)
            "com.noshufou.android.su",         // Superuser (old)
            "com.thirdparty.superuser",        // generic
            "com.yellowes.su",                 // Yellowes SU
            "com.kingroot.kinguser",           // KingRoot
            "com.kingo.root",                  // Kingo Root
        )
        val pm = context.packageManager
        if (rootApps.any { isPackageInstalled(pm, it) }) return true

        // 4. Can we actually execute `su`? (final arbiter)
        return try {
            Runtime.getRuntime().exec("which su").inputStream.bufferedReader().readLine() != null
        } catch (_: Exception) { false }
    }

    // ── Debugger detection ────────────────────────────────────────────────────

    private fun detectDebugger(): Boolean {
        // Java-level debugger
        if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) return true

        // Native debugger (ptrace) — check TracerPid in /proc/self/status
        return try {
            File("/proc/self/status").bufferedReader().useLines { lines ->
                lines.firstOrNull { it.startsWith("TracerPid:") }
                    ?.substringAfter(":")
                    ?.trim()
                    ?.toIntOrNull()
                    ?.let { it != 0 }
                    ?: false
            }
        } catch (_: Exception) { false }
    }

    // ── RE tool detection ─────────────────────────────────────────────────────

    private fun detectREtools(): Boolean {
        // Frida server leaves a characteristic file / port
        val fridaMarkers = arrayOf(
            "/data/local/tmp/frida-server",
            "/data/local/tmp/re.frida.server",
            "/system/lib/libfrida-gadget.so",
            "/system/lib64/libfrida-gadget.so",
        )
        if (fridaMarkers.any { File(it).exists() }) return true

        // Check if frida-gadget is loaded in our own process
        try {
            val maps = File("/proc/self/maps").readText()
            if (maps.contains("frida", ignoreCase = true) ||
                maps.contains("gadget", ignoreCase = true) ||
                maps.contains("xposed", ignoreCase = true)
            ) return true
        } catch (_: Exception) { /* ignore */ }

        // Xposed / LSPosed
        val xposedPackages = arrayOf(
            "de.robv.android.xposed.installer",
            "org.lsposed.manager",
            "io.va.exposed",
        )
        val pm = context.packageManager
        return xposedPackages.any { isPackageInstalled(pm, it) }
    }

    // ── Tamper detection ──────────────────────────────────────────────────────

    /**
     * APK signature check — verifies our APK was signed with the expected key.
     * Attacker repacking the APK will have a different signing key.
     *
     * The expected signature hash MUST be set at build time (e.g. via BuildConfig field)
     * from the release keystore. In debug builds this always returns false.
     */
    private fun detectTamper(): Boolean {
        // Debug builds are never "tampered" — dev keystore varies
        if (run.koto.BuildConfig.DEBUG) return false

        val expectedSignature = EXPECTED_APK_SIGNATURE_SHA256 ?: return false
        val actual = getOwnSignatureSha256() ?: return true // can't read = suspicious
        return !actual.equals(expectedSignature, ignoreCase = true)
    }

    @Suppress("DEPRECATION")
    private fun getOwnSignatureSha256(): String? = try {
        val pm = context.packageManager
        val signatures: Array<Signature> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val pkgInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            pkgInfo.signingInfo?.apkContentsSigners ?: emptyArray()
        } else {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures ?: emptyArray()
        }
        signatures.firstOrNull()?.let { sig ->
            val md = MessageDigest.getInstance("SHA-256")
            md.update(sig.toByteArray())
            md.digest().joinToString("") { "%02x".format(it) }
        }
    } catch (_: Exception) { null }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) { false }
    }

    companion object {
        /**
         * Expected SHA-256 of the release APK signing certificate, in hex.
         * Set this at release time from the real keystore:
         *   keytool -list -v -keystore release.keystore -alias koto | grep SHA256
         * Then paste the hex (without colons) here. Null → tamper check skipped.
         */
        private val EXPECTED_APK_SIGNATURE_SHA256: String? = null
    }
}
