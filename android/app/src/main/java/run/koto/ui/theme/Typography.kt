package run.koto.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import run.koto.R

/**
 * Inter Variable font family loaded from bundled TTF resource.
 *
 * DS-03: Typography scale uses Inter (variable font, ~860KB bundled in APK).
 * Variable font with FontVariation.Settings gives continuous weight axis from a single file.
 * Downloadable fonts API does NOT support variable fonts — bundling is mandatory here.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val interFontFamily = FontFamily(
    Font(
        resId             = R.font.inter_variable,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        resId             = R.font.inter_variable,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        resId             = R.font.inter_variable,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
        resId             = R.font.inter_variable,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

/**
 * Typography token system for KotoTheme.
 * 11 standard styles + 2 messenger-specific styles = 13 total.
 *
 * DS-03: Inter font, 8+ styles covering display through label.
 * Sizes and weights match the FEATURES.md typography specification table.
 */
@Immutable
data class KotoTypography(
    // ── Display ───────────────────────────────────────────────────────────────
    val displayLarge  : TextStyle,  // 28sp SemiBold  — onboarding titles
    val displayMedium : TextStyle,  // 24sp SemiBold  — section headers
    // ── Titles ────────────────────────────────────────────────────────────────
    val titleLarge    : TextStyle,  // 22sp SemiBold  — chat screen toolbar name
    val titleMedium   : TextStyle,  // 18sp Medium    — conversation name in list
    val titleSmall    : TextStyle,  // 16sp Medium    — dialog titles
    // ── Body ──────────────────────────────────────────────────────────────────
    val bodyLarge     : TextStyle,  // 16sp Regular   — conversation list preview
    val bodyMedium    : TextStyle,  // 15sp Regular   — chat messages
    val bodySmall     : TextStyle,  // 13sp Regular   — secondary text
    // ── Labels ────────────────────────────────────────────────────────────────
    val labelLarge    : TextStyle,  // 14sp Medium    — button text
    val labelMedium   : TextStyle,  // 12sp Medium    — timestamps, badges
    val labelSmall    : TextStyle,  // 11sp Medium    — typing indicator, status
    // ── Messenger-specific ────────────────────────────────────────────────────
    val chatBubble    : TextStyle,  // 15sp Regular   — message body in bubble
    val chatTimestamp : TextStyle,  // 11sp Normal    — small timestamp below bubble
)

/**
 * Default typography instance with Inter Variable and exact sizes from FEATURES.md.
 */
val defaultKotoTypography = KotoTypography(
    displayLarge  = TextStyle(
        fontFamily    = interFontFamily,
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 28.sp,
        lineHeight    = 36.sp,
        letterSpacing = (-0.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily    = interFontFamily,
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 24.sp,
        lineHeight    = 32.sp,
        letterSpacing = (-0.25).sp,
    ),
    titleLarge    = TextStyle(
        fontFamily    = interFontFamily,
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 22.sp,
        lineHeight    = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium   = TextStyle(
        fontFamily    = interFontFamily,
        fontWeight    = FontWeight.Medium,
        fontSize      = 18.sp,
        lineHeight    = 24.sp,
        letterSpacing = 0.sp,
    ),
    titleSmall    = TextStyle(
        fontFamily    = interFontFamily,
        fontWeight    = FontWeight.Medium,
        fontSize      = 16.sp,
        lineHeight    = 22.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge     = TextStyle(
        fontFamily    = interFontFamily,
        fontWeight    = FontWeight.Normal,
        fontSize      = 16.sp,
        lineHeight    = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    bodyMedium    = TextStyle(
        fontFamily    = interFontFamily,
        fontWeight    = FontWeight.Normal,
        fontSize      = 15.sp,
        lineHeight    = 22.sp,
        letterSpacing = 0.1.sp,
    ),
    bodySmall     = TextStyle(
        fontFamily    = interFontFamily,
        fontWeight    = FontWeight.Normal,
        fontSize      = 13.sp,
        lineHeight    = 18.sp,
        letterSpacing = 0.2.sp,
    ),
    labelLarge    = TextStyle(
        fontFamily    = interFontFamily,
        fontWeight    = FontWeight.Medium,
        fontSize      = 14.sp,
        lineHeight    = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium   = TextStyle(
        fontFamily    = interFontFamily,
        fontWeight    = FontWeight.Medium,
        fontSize      = 12.sp,
        lineHeight    = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall    = TextStyle(
        fontFamily    = interFontFamily,
        fontWeight    = FontWeight.Medium,
        fontSize      = 11.sp,
        lineHeight    = 14.sp,
        letterSpacing = 0.5.sp,
    ),
    chatBubble    = TextStyle(
        fontFamily    = interFontFamily,
        fontWeight    = FontWeight.Normal,
        fontSize      = 15.sp,
        lineHeight    = 21.sp,
        letterSpacing = 0.sp,
    ),
    chatTimestamp = TextStyle(
        fontFamily    = interFontFamily,
        fontWeight    = FontWeight.Normal,
        fontSize      = 11.sp,
        lineHeight    = 14.sp,
        letterSpacing = 0.sp,
    ),
)
