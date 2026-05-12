package run.koto.ui.theme

import androidx.compose.ui.graphics.Color

// ─── Avatar gradients (8 variants chosen by hash of user ID) ─────────────────
// These are decorative, not semantic tokens — raw colors are acceptable here.
private val AvatarGradients: List<List<Color>> = listOf(
    listOf(Color(0xFF5B21B6), Color(0xFF8B5CF6)),
    listOf(Color(0xFF1D4ED8), Color(0xFF60A5FA)),
    listOf(Color(0xFF065F46), Color(0xFF34D399)),
    listOf(Color(0xFF991B1B), Color(0xFFF87171)),
    listOf(Color(0xFF92400E), Color(0xFFFBBF24)),
    listOf(Color(0xFF1E3A5F), Color(0xFF67E8F9)),
    listOf(Color(0xFF3B0764), Color(0xFFE879F9)),
    listOf(Color(0xFF14532D), Color(0xFF86EFAC)),
)

/**
 * Returns one of 8 gradient color pairs for avatar backgrounds,
 * deterministically chosen by hashing the user's ID.
 */
fun avatarGradient(id: String): List<Color> =
    AvatarGradients[(id.hashCode().and(0x7FFFFFFF)) % AvatarGradients.size]
