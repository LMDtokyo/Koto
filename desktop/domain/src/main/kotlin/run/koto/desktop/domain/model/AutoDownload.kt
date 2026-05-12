package run.koto.desktop.domain.model

/**
 * Auto-download preferences. Per-media-type toggle + size cap (MB) for items
 * that can be large. A 0 cap is treated as "no limit".
 */
data class AutoDownloadPrefs(
    val photos     : Boolean = true,
    val videos     : Boolean = true,
    val videoMaxMb : Int     = 10,
    val files      : Boolean = true,
    val fileMaxMb  : Int     = 50,
    val voice      : Boolean = true,
)

data class NetworkStats(
    val sentBytes     : Long = 0L,
    val receivedBytes : Long = 0L,
    /** Epoch millis when these counters started accumulating. 0 = never reset. */
    val sinceAt       : Long = 0L,
)
