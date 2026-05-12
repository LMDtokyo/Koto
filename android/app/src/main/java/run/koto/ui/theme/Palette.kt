package run.koto.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Raw palette values — internal to the theme package.
 * No composable outside ui/theme/ should import from this file.
 * All downstream code references semantic tokens via KotoColors.
 */
internal object KotoPalette {
    // ── Brand violet (DS-02) ──────────────────────────────────────────────────
    val violet500  = Color(0xFF7B61FF)   // primary dark + light (DS-02 spec)
    val violet700  = Color(0xFF7C3AED)   // bubble gradient start (DS-02)
    val violet800  = Color(0xFF5B21B6)   // bubble gradient end (DS-02)
    val violet400  = Color(0xFF9D8FFF)   // light-mode primary variant
    val violetLight= Color(0xFF6C5CE7)   // FEATURES.md light primary

    // ── Dark theme surfaces ───────────────────────────────────────────────────
    val inkBlack   = Color(0xFF0A0A12)   // dark background (FEATURES.md)
    val surface0   = Color(0xFF14141F)   // dark surface
    val surface1   = Color(0xFF1E1E2E)   // dark surfaceVariant / received bubble
    val surface2   = Color(0xFF1A1A28)   // dark elevated surface
    val msgOut     = Color(0xFF2B2040)   // outgoing bubble flat bg (dark)
    val msgIn      = Color(0xFF1E1E2E)   // incoming bubble bg (dark)

    // ── Light theme surfaces ──────────────────────────────────────────────────
    val ghostWhite  = Color(0xFFFAFBFF)  // light background
    val white       = Color(0xFFFFFFFF)  // light surface
    val surfaceL1   = Color(0xFFF0F1F8)  // light surfaceVariant / received bubble
    val inkDark     = Color(0xFF1A1A2E)  // light onSurface primary text

    // ── Semantic utility ──────────────────────────────────────────────────────
    val onlineGreen = Color(0xFF34D399)  // dark online indicator (FEATURES.md)
    val onlineGreenL= Color(0xFF10B981)  // light online indicator
    val errorRed    = Color(0xFFF87171)  // dark error
    val errorRedL   = Color(0xFFEF4444)  // light error
}
