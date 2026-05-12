package run.koto.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Backward-compatibility aliases for the old Color.kt flat constants.
 * These point to semantic tokens in darkKotoColors / KotoPalette so that
 * existing screen files continue to compile without modification.
 *
 * TODO (Plan 05): Remove this file once all composables migrate to KotoTheme.colors.*
 *
 * [Rule 3 - Blocking] Auto-added by Plan 02 executor to preserve compile compatibility
 * after removing old Color.kt top-level vals.
 */

// ── Backgrounds ────────────────────────────────────────────────────────────────
val BgBase        = KotoPalette.inkBlack
val BgPrimary     = KotoPalette.inkBlack
val BgSecondary   = KotoPalette.surface0
val BgElevated    = KotoPalette.surface1
val BgInput       = KotoPalette.surface1
val BgSurface1    = KotoPalette.surface1

// ── Accent ─────────────────────────────────────────────────────────────────────
val AccentPrimary       = KotoPalette.violet500
val AccentLight         = KotoPalette.violet400
val AccentDark          = KotoPalette.violet700
val AccentUltraLight    = Color(0xFFC4B5FD)
val AccentContainer     = Color(0xFF1E1230)
val AccentGradientStart = KotoPalette.violet700
val AccentGradientEnd   = Color(0xFF8B5CF6)
val BubbleGradientStart = KotoPalette.violet700
val BubbleGradientMid   = KotoPalette.violet700
val BubbleGradientEnd   = Color(0xFF8B5CF6)

// ── Text ───────────────────────────────────────────────────────────────────────
val TextPrimary    = Color(0xFFE5E7EB)
val TextSecondary  = Color(0xFF9CA3AF)
val TextTertiary   = Color(0xFF6B7280)
val TextDisabled   = Color(0xFF4B5563)

// ── Status ─────────────────────────────────────────────────────────────────────
val OnlineGreen    = KotoPalette.onlineGreen
val WarningAmber   = Color(0xFFF59E0B)
val ErrorRed       = KotoPalette.errorRed
val ReadBlue       = Color(0xFF60A5FA)

// ── Chrome ─────────────────────────────────────────────────────────────────────
val DividerColor   = KotoPalette.surface1
val IconDefault    = Color(0xFF9CA3AF)
val IconActive     = KotoPalette.violet400
val Ripple         = Color(0x1AFFFFFF)

// ── Bubbles ────────────────────────────────────────────────────────────────────
val BubbleReceived       = KotoPalette.msgIn
val BubbleReceivedBorder = KotoPalette.surface1
