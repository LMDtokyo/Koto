package run.koto.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ─── CompositionLocals ────────────────────────────────────────────────────────
//
// staticCompositionLocalOf: best read performance for values that rarely change.
// Invalidates the entire subtree on change — acceptable since theme switches are rare
// and the 300ms color animation distributes recomposition over multiple frames.
//
val LocalKotoColors     = staticCompositionLocalOf { darkKotoColors }
val LocalKotoTypography = staticCompositionLocalOf { defaultKotoTypography }
val LocalKotoSpacing    = staticCompositionLocalOf { KotoSpacing() }
val LocalKotoShapes     = staticCompositionLocalOf { KotoShapes() }
val LocalKotoElevation  = staticCompositionLocalOf { KotoElevation() }
val LocalKotoMotion     = staticCompositionLocalOf { DefaultKotoMotion }

// ─── Accessor object ──────────────────────────────────────────────────────────
//
// Singleton with @Composable getters — call site: KotoTheme.colors.primary
// No parentheses, no CompositionLocal.current boilerplate at call sites.
//
object KotoTheme {
    val colors     : KotoColors     @Composable get() = LocalKotoColors.current
    val typography : KotoTypography @Composable get() = LocalKotoTypography.current
    val spacing    : KotoSpacing    @Composable get() = LocalKotoSpacing.current
    val shapes     : KotoShapes     @Composable get() = LocalKotoShapes.current
    val elevation  : KotoElevation  @Composable get() = LocalKotoElevation.current
    val motion     : KotoMotion     @Composable get() = LocalKotoMotion.current
}

// ─── Animated color transition ────────────────────────────────────────────────
//
// DS-06: Animate each Color field individually. Do NOT use Crossfade (it destroys
// and rebuilds the entire subtree). animateColorAsState keeps the composition stable.
//
// bubbleGradient (Brush) and isLight (Boolean) are passed through directly —
// Brush has no animateBrushAsState API; Boolean cannot be interpolated.
//
@Composable
private fun animateKotoColors(target: KotoColors): KotoColors {
    val spec = tween<Color>(durationMillis = 300, easing = FastOutSlowInEasing)
    return KotoColors(
        primary        = animateColorAsState(target.primary,        spec, "primary").value,
        onPrimary      = animateColorAsState(target.onPrimary,      spec, "onPrimary").value,
        background     = animateColorAsState(target.background,     spec, "background").value,
        surface        = animateColorAsState(target.surface,        spec, "surface").value,
        surfaceVariant = animateColorAsState(target.surfaceVariant, spec, "surfaceVariant").value,
        onBackground   = animateColorAsState(target.onBackground,   spec, "onBackground").value,
        onSurface      = animateColorAsState(target.onSurface,      spec, "onSurface").value,
        onSurfaceLow   = animateColorAsState(target.onSurfaceLow,   spec, "onSurfaceLow").value,
        onSurfaceMuted = animateColorAsState(target.onSurfaceMuted, spec, "onSurfaceMuted").value,
        bubbleOut      = animateColorAsState(target.bubbleOut,      spec, "bubbleOut").value,
        bubbleIn       = animateColorAsState(target.bubbleIn,       spec, "bubbleIn").value,
        onBubbleOut    = animateColorAsState(target.onBubbleOut,    spec, "onBubbleOut").value,
        onBubbleIn     = animateColorAsState(target.onBubbleIn,     spec, "onBubbleIn").value,
        bubbleGradient = target.bubbleGradient,   // Brush — not animatable, swap directly
        divider        = animateColorAsState(target.divider,        spec, "divider").value,
        error          = animateColorAsState(target.error,          spec, "error").value,
        warning        = animateColorAsState(target.warning,        spec, "warning").value,
        success        = animateColorAsState(target.success,        spec, "success").value,
        online         = animateColorAsState(target.online,         spec, "online").value,
        isLight        = target.isLight,          // Boolean — not animatable
    )
}

// ─── KotoTheme provider ───────────────────────────────────────────────────────
//
// Wrap with CompositionLocalProvider first (so KotoTheme.colors.* works inside),
// then wrap MaterialTheme so Material components (ModalBottomSheet, Snackbar, etc.)
// receive a matching color scheme.
//
@Composable
fun KotoTheme(
    darkTheme : Boolean = isSystemInDarkTheme(),
    content   : @Composable () -> Unit,
) {
    val target         = if (darkTheme) darkKotoColors else lightKotoColors
    val animatedColors = animateKotoColors(target)

    // Material3 color scheme aligned with KotoColors for built-in components
    val m3Scheme = if (darkTheme) {
        darkColorScheme(
            primary          = animatedColors.primary,
            onPrimary        = animatedColors.onPrimary,
            background       = animatedColors.background,
            onBackground     = animatedColors.onBackground,
            surface          = animatedColors.surface,
            onSurface        = animatedColors.onSurface,
            surfaceVariant   = animatedColors.surfaceVariant,
            onSurfaceVariant = animatedColors.onSurfaceLow,
            error            = animatedColors.error,
            outline          = animatedColors.divider,
        )
    } else {
        lightColorScheme(
            primary          = animatedColors.primary,
            onPrimary        = animatedColors.onPrimary,
            background       = animatedColors.background,
            onBackground     = animatedColors.onBackground,
            surface          = animatedColors.surface,
            onSurface        = animatedColors.onSurface,
            surfaceVariant   = animatedColors.surfaceVariant,
            onSurfaceVariant = animatedColors.onSurfaceLow,
            error            = animatedColors.error,
            outline          = animatedColors.divider,
        )
    }

    CompositionLocalProvider(
        LocalKotoColors     provides animatedColors,
        LocalKotoTypography provides defaultKotoTypography,
        LocalKotoSpacing    provides KotoSpacing(),
        LocalKotoShapes     provides KotoShapes(),
        LocalKotoElevation  provides KotoElevation(),
        LocalKotoMotion     provides DefaultKotoMotion,
    ) {
        MaterialTheme(
            colorScheme = m3Scheme,
            content     = content,
        )
    }
}
