package run.koto.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Semantic color tokens for KotoTheme.
 * Access via KotoTheme.colors.* — never reference KotoPalette directly in composables.
 *
 * DS-01: Semantic token set (primary, surface, onSurface, etc.)
 * DS-02: Exact palette values — violet500 = #7B61FF primary; bubble gradient #7C3AED → #5B21B6
 */
@Immutable
data class KotoColors(
    // ── Brand ─────────────────────────────────────────────────────────────────
    val primary        : Color,
    val onPrimary      : Color,
    // ── Surfaces ──────────────────────────────────────────────────────────────
    val background     : Color,
    val surface        : Color,
    val surfaceVariant : Color,
    // ── Content ───────────────────────────────────────────────────────────────
    val onBackground   : Color,
    val onSurface      : Color,
    val onSurfaceLow   : Color,     // secondary text — 60% opacity
    val onSurfaceMuted : Color,     // timestamps, hints — 38% opacity
    // ── Chat-specific ─────────────────────────────────────────────────────────
    val bubbleOut      : Color,     // outgoing bubble flat color (gradient is bubbleGradient)
    val bubbleIn       : Color,     // incoming bubble background
    val onBubbleOut    : Color,     // text on sent bubbles
    val onBubbleIn     : Color,     // text on received bubbles
    val bubbleGradient : Brush,     // sent bubble gradient (DS-02) — NOT animated, swap directly
    // ── Utility ───────────────────────────────────────────────────────────────
    val divider        : Color,
    val error          : Color,
    val warning        : Color,
    val success        : Color,
    val online         : Color,     // presence dot
    val isLight        : Boolean,
)

// DS-02: bubble gradient at 135 degrees — #7C3AED (start) → #5B21B6 (end)
private val darkBubbleGradient = Brush.linearGradient(
    colors = listOf(KotoPalette.violet700, KotoPalette.violet800),
    start  = androidx.compose.ui.geometry.Offset(0f, 0f),
    end    = androidx.compose.ui.geometry.Offset(1f, 1f),  // diagonal ~ 135 deg
)

private val lightBubbleGradient = Brush.linearGradient(
    colors = listOf(KotoPalette.violetLight, Color(0xFF8B7CF7)),
    start  = androidx.compose.ui.geometry.Offset(0f, 0f),
    end    = androidx.compose.ui.geometry.Offset(1f, 1f),
)

/**
 * Dark theme tokens. primary = #7B61FF per DS-02.
 */
val darkKotoColors = KotoColors(
    primary        = KotoPalette.violet500,          // #7B61FF (DS-02)
    onPrimary      = Color.White,
    background     = KotoPalette.inkBlack,           // #0A0A12
    surface        = KotoPalette.surface0,           // #14141F
    surfaceVariant = KotoPalette.surface1,           // #1E1E2E
    onBackground   = Color(0xFFE5E7EB),
    onSurface      = Color(0xFFE5E7EB),
    onSurfaceLow   = Color(0xFFE5E7EB).copy(alpha = 0.60f),
    onSurfaceMuted = Color(0xFFE5E7EB).copy(alpha = 0.38f),
    bubbleOut      = KotoPalette.msgOut,             // #2B2040 flat bg (gradient overlaid in composable)
    bubbleIn       = KotoPalette.msgIn,              // #1E1E2E
    onBubbleOut    = Color.White,
    onBubbleIn     = Color(0xFFE5E7EB),
    bubbleGradient = darkBubbleGradient,             // DS-02: #7C3AED → #5B21B6
    divider        = Color.White.copy(alpha = 0.08f),
    error          = KotoPalette.errorRed,           // #F87171
    warning        = Color(0xFFFF9F0A),              // amber warning
    success        = KotoPalette.onlineGreen,        // #34D399
    online         = KotoPalette.onlineGreen,        // #34D399
    isLight        = false,
)

/**
 * Light theme tokens. primary = #7B61FF per DS-02 (same as dark per REQUIREMENTS.md DS-02).
 */
val lightKotoColors = KotoColors(
    primary        = KotoPalette.violet500,          // #7B61FF (DS-02 — same for both themes)
    onPrimary      = Color.White,
    background     = KotoPalette.ghostWhite,         // #FAFBFF
    surface        = KotoPalette.white,              // #FFFFFF
    surfaceVariant = KotoPalette.surfaceL1,          // #F0F1F8
    onBackground   = KotoPalette.inkDark,            // #1A1A2E
    onSurface      = KotoPalette.inkDark,            // #1A1A2E
    onSurfaceLow   = KotoPalette.inkDark.copy(alpha = 0.60f),
    onSurfaceMuted = KotoPalette.inkDark.copy(alpha = 0.38f),
    bubbleOut      = KotoPalette.violet500,          // flat bg (gradient overlaid)
    bubbleIn       = KotoPalette.surfaceL1,          // #F0F1F8
    onBubbleOut    = Color.White,
    onBubbleIn     = KotoPalette.inkDark,
    bubbleGradient = lightBubbleGradient,            // #6C5CE7 → #8B7CF7
    divider        = KotoPalette.inkDark.copy(alpha = 0.08f),
    error          = KotoPalette.errorRedL,          // #EF4444
    warning        = Color(0xFFE69500),              // amber warning (light)
    success        = KotoPalette.onlineGreenL,       // #10B981
    online         = KotoPalette.onlineGreenL,       // #10B981
    isLight        = true,
)
