package run.koto.desktop.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Raw palette values — internal to the theme package. Mirrors the CSS custom
 * properties in the design mockup's `styles.css`:
 *
 *   --accent:          #ff6b35
 *   --accent-pressed:  #e55a26
 *   --accent-glow:     rgba(255,107,53,0.35)
 *
 * No composable outside `ui/theme/` should read these directly — use the semantic
 * tokens exposed by [KotoColors] via `KotoTheme.colors.*`.
 */
internal object KotoPalette {
    // ── Brand orange ──────────────────────────────────────────────────────────
    val accent        = Color(0xFFFF6B35)
    val accentPressed = Color(0xFFE55A26)
    val accentSoft    = Color(0xFFFF8656)          // gradient start, lighter tint
    val accentDeep    = Color(0xFFE5531E)          // gradient end, deeper shade
    val accentGlow    = Color(0x59FF6B35)          // 35% opacity halo around avatar / call

    // ── Dark surfaces ─────────────────────────────────────────────────────────
    val darkBg        = Color(0xFF0B0B0C)
    val darkSurface   = Color(0xFF1A1A1C)
    val darkElevated  = Color(0xFF222224)
    val darkText      = Color(0xFFFFFFFF)
    val darkTextSec   = Color(0xFF9E9E9E)
    val darkTextTer   = Color(0xFF5E5E62)
    val darkSeparator = Color(0x14FFFFFF)          // rgba(255,255,255,0.08)
    val darkPeerBubble= Color(0xFF27272A)

    // ── Light surfaces ────────────────────────────────────────────────────────
    val lightBg        = Color(0xFFFFFFFF)
    val lightSurface   = Color(0xFFF2F2F2)
    val lightElevated  = Color(0xFFFFFFFF)
    val lightText      = Color(0xFF0A0A0A)
    val lightTextSec   = Color(0xFF707070)
    val lightTextTer   = Color(0xFFA8A8A8)
    val lightSeparator = Color(0x14000000)          // rgba(0,0,0,0.08)
    val lightPeerBubble= Color(0xFFECECEC)

    // ── iOS semantic accents (shared across light/dark) ──────────────────────
    val success       = Color(0xFF34C759)           // unarchive, verified, online dot
    val error         = Color(0xFFFF3B30)           // destructive actions (light)
    val errorDark     = Color(0xFFFF453A)           // destructive actions (dark)
    val info          = Color(0xFF0A84FF)           // read-status blue, secondary actions
    val warning       = Color(0xFFFF9F0A)           // pin action tray
    val muted         = Color(0xFF8E8E93)           // mute tray, generic iOS gray
}
