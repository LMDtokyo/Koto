package run.koto.desktop.domain.model

/**
 * User-tunable preferences that live entirely on the client. Read receipts,
 * typing indicators and online presence are *broadcast* signals — gating
 * them client-side prevents that data from ever reaching the wire, which is
 * the correct privacy posture for an E2EE messenger.
 *
 * `notif_preview` controls whether decrypted message text appears in OS
 * toasts; `discoverable` controls whether the public username lookup
 * resolves to this account (server-enforced once that field exists on the
 * profile schema).
 */
data class AppPreferences(
    val privacyPreset    : PrivacyPreset = PrivacyPreset.Standard,
    val sendReadReceipts : Boolean       = true,
    val sendTyping       : Boolean       = true,
    val showOnlineStatus : Boolean       = true,
    val notifPreview     : Boolean       = true,
    val discoverable     : Boolean       = true,
)

enum class PrivacyPreset(val wireName: String) {
    Standard ("standard"),
    Contacts ("contacts"),
    Paranoid ("paranoid");

    companion object {
        fun parse(raw: String?): PrivacyPreset = entries.firstOrNull { it.wireName == raw } ?: Standard
    }
}

/** Apply [preset] semantics on top of an existing [AppPreferences]. */
fun AppPreferences.withPreset(preset: PrivacyPreset): AppPreferences = when (preset) {
    PrivacyPreset.Standard -> copy(
        privacyPreset    = preset,
        sendReadReceipts = true,
        sendTyping       = true,
        showOnlineStatus = true,
        notifPreview     = true,
        discoverable     = true,
    )
    PrivacyPreset.Contacts -> copy(
        privacyPreset    = preset,
        sendReadReceipts = true,
        sendTyping       = true,
        showOnlineStatus = true,
        notifPreview     = true,
        discoverable     = false,
    )
    PrivacyPreset.Paranoid -> copy(
        privacyPreset    = preset,
        sendReadReceipts = false,
        sendTyping       = false,
        showOnlineStatus = false,
        notifPreview     = false,
        discoverable     = false,
    )
}
