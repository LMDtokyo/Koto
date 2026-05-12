package run.koto.desktop.data.remote

import java.net.InetAddress

/**
 * Device metadata sent to the auth service on register/restore so the user
 * can recognise this device in their linked-devices list. Mirrors what
 * Telegram sends as `system_version` / `device_model` / `app_version`.
 */
data class DeviceInfo(
    val name       : String,
    val platform   : String,
    val appVersion : String,
)

/**
 * Best-effort detection of the current desktop. Falls back to "Koto Desktop"
 * + the OS family if the hostname cannot be read (sandboxed environments).
 */
object LocalDevice {
    val info: DeviceInfo by lazy {
        val osName = System.getProperty("os.name").orEmpty()
        val platform = when {
            osName.contains("win",    ignoreCase = true) -> "Windows"
            osName.contains("mac",    ignoreCase = true) -> "macOS"
            osName.contains("linux",  ignoreCase = true) -> "Linux"
            else -> osName.ifBlank { "Desktop" }
        }
        val hostname = runCatching { InetAddress.getLocalHost().hostName }.getOrNull()
            ?.takeIf { it.isNotBlank() && !it.equals("localhost", ignoreCase = true) }
        val name = hostname?.let { "Koto Desktop ($it)" } ?: "Koto Desktop ($platform)"
        DeviceInfo(name = name, platform = platform, appVersion = APP_VERSION)
    }

    private const val APP_VERSION = "26.4.1"
}
