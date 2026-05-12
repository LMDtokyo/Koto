package run.koto.desktop.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Semantic color tokens for Koto. Every composable reaches colors through
 * `KotoTheme.colors.*`, never via [KotoPalette] directly.
 *
 * Naming is driven by the design mockup's usage patterns — `accent` / `accentGlow`
 * instead of Material's `primary` / `primaryContainer` so motion specs (auroraDrift,
 * pulseRing, bubble gradients) line up with the CSS keyframes byte-for-byte.
 */
@Immutable
data class KotoColors(
    // ── Brand ─────────────────────────────────────────────────────────────────
    val accent         : Color,
    val accentPressed  : Color,
    val accentGlow     : Color,
    val onAccent       : Color,
    val accentGradient : Brush,     // 135° soft→deep, for logo tiles

    // ── Surfaces ──────────────────────────────────────────────────────────────
    val background     : Color,     // window bg behind everything
    val surface        : Color,     // cards, sheets
    val elevated       : Color,     // popovers, tooltips, higher-layer chips

    // ── Text ──────────────────────────────────────────────────────────────────
    val text           : Color,     // primary
    val textSecondary  : Color,     // hints, timestamps, supporting copy
    val textTertiary   : Color,     // disabled, labels on elevated tiers

    // ── Bubbles ───────────────────────────────────────────────────────────────
    val bubbleSelf     : Color,     // outgoing background (filled by [accent])
    val bubblePeer     : Color,     // incoming background
    val onBubbleSelf   : Color,
    val onBubblePeer   : Color,

    // ── Utility / status ──────────────────────────────────────────────────────
    val separator      : Color,
    val success        : Color,
    val error          : Color,
    val info           : Color,
    val warning        : Color,
    val muted          : Color,

    // ── Meta ──────────────────────────────────────────────────────────────────
    val isLight        : Boolean,
)

private val accentGradientAll = Brush.linearGradient(
    colors = listOf(KotoPalette.accentSoft, KotoPalette.accent, KotoPalette.accentDeep),
    start  = Offset(0f, 0f),
    end    = Offset(1f, 1f),     // 135° diagonal
)

val darkKotoColors = KotoColors(
    accent         = KotoPalette.accent,
    accentPressed  = KotoPalette.accentPressed,
    accentGlow     = KotoPalette.accentGlow,
    onAccent       = Color.White,
    accentGradient = accentGradientAll,

    background     = KotoPalette.darkBg,
    surface        = KotoPalette.darkSurface,
    elevated       = KotoPalette.darkElevated,

    text           = KotoPalette.darkText,
    textSecondary  = KotoPalette.darkTextSec,
    textTertiary   = KotoPalette.darkTextTer,

    bubbleSelf     = KotoPalette.accent,          // self bubbles are brand orange
    bubblePeer     = KotoPalette.darkPeerBubble,
    onBubbleSelf   = Color.White,
    onBubblePeer   = KotoPalette.darkText,

    separator      = KotoPalette.darkSeparator,
    success        = KotoPalette.success,
    error          = KotoPalette.errorDark,
    info           = KotoPalette.info,
    warning        = KotoPalette.warning,
    muted          = KotoPalette.muted,

    isLight        = false,
)

val lightKotoColors = KotoColors(
    accent         = KotoPalette.accent,
    accentPressed  = KotoPalette.accentPressed,
    accentGlow     = KotoPalette.accentGlow,
    onAccent       = Color.White,
    accentGradient = accentGradientAll,

    background     = KotoPalette.lightBg,
    surface        = KotoPalette.lightSurface,
    elevated       = KotoPalette.lightElevated,

    text           = KotoPalette.lightText,
    textSecondary  = KotoPalette.lightTextSec,
    textTertiary   = KotoPalette.lightTextTer,

    bubbleSelf     = KotoPalette.accent,
    bubblePeer     = KotoPalette.lightPeerBubble,
    onBubbleSelf   = Color.White,
    onBubblePeer   = KotoPalette.lightText,

    separator      = KotoPalette.lightSeparator,
    success        = KotoPalette.success,
    error          = KotoPalette.error,
    info           = KotoPalette.info,
    warning        = KotoPalette.warning,
    muted          = KotoPalette.muted,

    isLight        = true,
)
