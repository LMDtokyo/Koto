package run.koto.desktop.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalKotoColors     = staticCompositionLocalOf<KotoColors> { error("KotoTheme: no KotoColors in scope") }
private val LocalKotoTypography = staticCompositionLocalOf { DefaultKotoTypography }
private val LocalKotoShapes     = staticCompositionLocalOf { DefaultKotoShapes }
private val LocalKotoSpacing    = staticCompositionLocalOf { DefaultKotoSpacing }
private val LocalKotoElevation  = staticCompositionLocalOf { DefaultKotoElevation }
private val LocalKotoMotion     = staticCompositionLocalOf { DefaultKotoMotion }

/**
 * Root theme scope. Colors animate over 300 ms via [animateColorAsState] so toggling
 * dark/light produces the same soft crossfade as the mockup's CSS transition, not an
 * instant snap.
 *
 * Brushes (accentGradient) are swapped directly — Compose has no brush interpolation.
 * The surrounding color tween carries enough motion that the user perceives this as
 * one coherent change.
 */
@Composable
fun KotoTheme(
    darkTheme : Boolean,
    content   : @Composable () -> Unit,
) {
    val base   = if (darkTheme) darkKotoColors else lightKotoColors
    val colors = animateKotoColors(base)

    CompositionLocalProvider(
        LocalKotoColors     provides colors,
        LocalKotoTypography provides DefaultKotoTypography,
        LocalKotoShapes     provides DefaultKotoShapes,
        LocalKotoSpacing    provides DefaultKotoSpacing,
        LocalKotoElevation  provides DefaultKotoElevation,
        LocalKotoMotion     provides DefaultKotoMotion,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) {
                darkColorScheme(
                    primary    = colors.accent,
                    onPrimary  = colors.onAccent,
                    background = colors.background,
                    surface    = colors.surface,
                    error      = colors.error,
                )
            } else {
                lightColorScheme(
                    primary    = colors.accent,
                    onPrimary  = colors.onAccent,
                    background = colors.background,
                    surface    = colors.surface,
                    error      = colors.error,
                )
            },
            content = content,
        )
    }
}

object KotoTheme {
    val colors    : KotoColors     @Composable @ReadOnlyComposable get() = LocalKotoColors.current
    val typography: KotoTypography @Composable @ReadOnlyComposable get() = LocalKotoTypography.current
    val shapes    : KotoShapes     @Composable @ReadOnlyComposable get() = LocalKotoShapes.current
    val spacing   : KotoSpacing    @Composable @ReadOnlyComposable get() = LocalKotoSpacing.current
    val elevation : KotoElevation  @Composable @ReadOnlyComposable get() = LocalKotoElevation.current
    val motion    : KotoMotion     @Composable @ReadOnlyComposable get() = LocalKotoMotion.current
}

@Composable
private fun animateKotoColors(target: KotoColors): KotoColors {
    val spec = tween<androidx.compose.ui.graphics.Color>(durationMillis = 300)
    val accent        by animateColorAsState(target.accent,        spec, label = "accent")
    val accentPressed by animateColorAsState(target.accentPressed, spec, label = "accentPressed")
    val accentGlow    by animateColorAsState(target.accentGlow,    spec, label = "accentGlow")
    val onAccent      by animateColorAsState(target.onAccent,      spec, label = "onAccent")
    val background    by animateColorAsState(target.background,    spec, label = "background")
    val surface       by animateColorAsState(target.surface,       spec, label = "surface")
    val elevated      by animateColorAsState(target.elevated,      spec, label = "elevated")
    val text          by animateColorAsState(target.text,          spec, label = "text")
    val textSecondary by animateColorAsState(target.textSecondary, spec, label = "textSecondary")
    val textTertiary  by animateColorAsState(target.textTertiary,  spec, label = "textTertiary")
    val bubbleSelf    by animateColorAsState(target.bubbleSelf,    spec, label = "bubbleSelf")
    val bubblePeer    by animateColorAsState(target.bubblePeer,    spec, label = "bubblePeer")
    val onBubbleSelf  by animateColorAsState(target.onBubbleSelf,  spec, label = "onBubbleSelf")
    val onBubblePeer  by animateColorAsState(target.onBubblePeer,  spec, label = "onBubblePeer")
    val separator     by animateColorAsState(target.separator,     spec, label = "separator")
    val success       by animateColorAsState(target.success,       spec, label = "success")
    val error         by animateColorAsState(target.error,         spec, label = "error")
    val info          by animateColorAsState(target.info,          spec, label = "info")
    val warning       by animateColorAsState(target.warning,       spec, label = "warning")
    val muted         by animateColorAsState(target.muted,         spec, label = "muted")
    return KotoColors(
        accent = accent, accentPressed = accentPressed, accentGlow = accentGlow, onAccent = onAccent,
        accentGradient = target.accentGradient,
        background = background, surface = surface, elevated = elevated,
        text = text, textSecondary = textSecondary, textTertiary = textTertiary,
        bubbleSelf = bubbleSelf, bubblePeer = bubblePeer, onBubbleSelf = onBubbleSelf, onBubblePeer = onBubblePeer,
        separator = separator, success = success, error = error, info = info, warning = warning, muted = muted,
        isLight = target.isLight,
    )
}
