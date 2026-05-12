package run.koto.desktop.domain.model

data class Account(
    val accountId    : String,
    val displayName  : String,
    val avatarUrl    : String?,
    val publicKey    : ByteArray,
)

data class Session(
    val accessToken  : String,
    val refreshToken : String,
    val accountId    : String,
    val sessionId    : String,
    val expiresAt    : Long,
)

/**
 * One linked device — what the user sees in Settings → Связанные устройства.
 * Mirrors the auth service's session row, minus the refresh_token_hash.
 */
data class LinkedDevice(
    val id          : String,
    val deviceName  : String,
    val platform    : String,
    val appVersion  : String,
    val clientIp    : String,
    val createdAt   : Long,
    val lastSeenAt  : Long,
)
