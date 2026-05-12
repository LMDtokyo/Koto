package run.koto.desktop.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Koto typography scale — faithful to the mockup.
 *
 * Fonts: Inter (variable) for UI text, JetBrains Mono for account IDs / seed words /
 * live timers. Both are bundled in :ui resources under `fonts/`. On first build the
 * TTFs must exist; until they do, Compose falls back to [FontFamily.Default] and the
 * weights still render (degrades gracefully).
 *
 * Letter-spacing values match `letter-spacing: -0.01em` → `(-0.16).sp` for 16sp body,
 * etc. Measured from the inline style strings in the mockup's screen files.
 */
@Immutable
data class KotoTypography(
    // Display — auth headline, full-screen call timer
    val displayXL     : TextStyle,
    val displayLarge  : TextStyle,
    val displayMedium : TextStyle,

    // Headline — screen titles, section headers
    val headlineLarge : TextStyle,
    val headlineMedium: TextStyle,
    val headlineSmall : TextStyle,

    // Titles — nav labels, card titles
    val titleLarge    : TextStyle,
    val titleMedium   : TextStyle,
    val titleSmall    : TextStyle,

    // Body — default text, messages, descriptions
    val bodyLarge     : TextStyle,
    val bodyMedium    : TextStyle,
    val bodySmall     : TextStyle,

    // Labels — chip text, button label, timestamp, badges
    val labelLarge    : TextStyle,
    val labelMedium   : TextStyle,
    val labelSmall    : TextStyle,

    // Chat-specific
    val bubble        : TextStyle,   // message bubble body
    val bubbleCompact : TextStyle,   // grouped / reply preview
    val timestamp     : TextStyle,
    val mention       : TextStyle,

    // Monospace — account ids, seed words, timers
    val mono          : TextStyle,
    val monoLarge     : TextStyle,
    val monoSmall     : TextStyle,
)

private fun loadFontBytes(path: String): ByteArray =
    object {}.javaClass.classLoader!!.getResourceAsStream(path)!!.use { it.readBytes() }

private val interBytes = loadFontBytes("fonts/Inter-Variable.ttf")
private val jbMonoBytes = loadFontBytes("fonts/JetBrainsMono-Variable.ttf")

private fun interFont(weight: FontWeight) = androidx.compose.ui.text.platform.Font(
    identity = "Inter", data = interBytes, weight = weight,
)
private fun jbMonoFont(weight: FontWeight) = androidx.compose.ui.text.platform.Font(
    identity = "JetBrainsMono", data = jbMonoBytes, weight = weight,
)

private val interFamily: FontFamily = FontFamily(
    interFont(FontWeight.Normal),
    interFont(FontWeight.Medium),
    interFont(FontWeight.SemiBold),
    interFont(FontWeight.Bold),
    interFont(FontWeight.ExtraBold),
)
private val jetbrainsMono: FontFamily = FontFamily(
    jbMonoFont(FontWeight.Normal),
    jbMonoFont(FontWeight.Medium),
    jbMonoFont(FontWeight.SemiBold),
    jbMonoFont(FontWeight.Bold),
)

val DefaultKotoTypography = KotoTypography(
    displayXL      = TextStyle(fontFamily = interFamily, fontSize = 46.sp, lineHeight = 52.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.4).sp),
    displayLarge   = TextStyle(fontFamily = interFamily, fontSize = 40.sp, lineHeight = 46.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.2).sp),
    displayMedium  = TextStyle(fontFamily = interFamily, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.0).sp),

    headlineLarge  = TextStyle(fontFamily = interFamily, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold,     letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontFamily = interFamily, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold,     letterSpacing = (-0.6).sp),
    headlineSmall  = TextStyle(fontFamily = interFamily, fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold,     letterSpacing = (-0.4).sp),

    titleLarge     = TextStyle(fontFamily = interFamily, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    titleMedium    = TextStyle(fontFamily = interFamily, fontSize = 15.5.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleSmall     = TextStyle(fontFamily = interFamily, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),

    bodyLarge      = TextStyle(fontFamily = interFamily, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal,   letterSpacing = (-0.2).sp),
    bodyMedium     = TextStyle(fontFamily = interFamily, fontSize = 14.5.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.15).sp),
    bodySmall      = TextStyle(fontFamily = interFamily, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal,   letterSpacing = (-0.1).sp),

    labelLarge     = TextStyle(fontFamily = interFamily, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium,   letterSpacing = 0.1.sp),
    labelMedium    = TextStyle(fontFamily = interFamily, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium,   letterSpacing = 0.2.sp),
    labelSmall     = TextStyle(fontFamily = interFamily, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium,   letterSpacing = 0.3.sp),

    bubble         = TextStyle(fontFamily = interFamily, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal,   letterSpacing = (-0.15).sp),
    bubbleCompact  = TextStyle(fontFamily = interFamily, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Normal,   letterSpacing = (-0.1).sp),
    timestamp      = TextStyle(fontFamily = interFamily, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium,   letterSpacing = 0.1.sp),
    mention        = TextStyle(fontFamily = interFamily, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.15).sp),

    mono           = TextStyle(fontFamily = jetbrainsMono, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.2.sp),
    monoLarge      = TextStyle(fontFamily = jetbrainsMono, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
    monoSmall      = TextStyle(fontFamily = jetbrainsMono, fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.4.sp),
)
