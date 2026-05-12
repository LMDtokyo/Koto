package run.koto.desktop.ui.screens.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.useResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.Image as SkiaImage
import run.koto.desktop.ui.theme.KotoTheme

/**
 * Auth-pane background. Intentionally minimal — Telegram Desktop style.
 * Solid base color with a very subtle vertical gradient so the top edge has
 * one shade of "lift" and the rest is flat. No blobs, no grid, no textures:
 * the brand signal comes from the logo tile and the orange CTA buttons.
 *
 * Picks its two stops from [KotoTheme.colors] so the backdrop automatically
 * tracks theme changes.
 */
@Composable
fun AuthBackdrop(modifier: Modifier = Modifier) {
    val colors = KotoTheme.colors
    // Keep the highlight subtle — we want a near-flat Telegram-like feel.
    val topTint = if (colors.isLight) Color.White
                  else colors.background.lighten(0.035f)
    val bottom  = colors.background

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(topTint, bottom),
                ),
            ),
    )
}

/**
 * Koto brand logo tile — iOS-style rounded square with the Koto cat emblem on
 * an orange gradient. The PNG is decoded once and held in [remember] so
 * re-renders (entrance, theme flip) don't re-parse the image.
 */
@Composable
fun KotoLogoTile(size: Dp = 104.dp, modifier: Modifier = Modifier) {
    val catPainter = remember {
        val bytes = useResource("koto-cat.png") { it.readBytes() }
        BitmapPainter(SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap())
    }

    Box(
        modifier          = modifier
            .size(size)
            .clip(KotoTheme.shapes.logoTile)
            .background(KotoTheme.colors.accentGradient),
        contentAlignment  = Alignment.Center,
    ) {
        Image(
            painter            = catPainter,
            contentDescription = "Koto",
            modifier           = Modifier.size(size * 0.82f),
            contentScale       = ContentScale.Fit,
        )
    }
}

/** Lightens a color toward pure white by [t] (0..1). Used for the backdrop
 *  top stop where we need 3–5 % extra luminance above the base. */
private fun Color.lighten(t: Float): Color = Color(
    red   = red   + (1f - red)   * t,
    green = green + (1f - green) * t,
    blue  = blue  + (1f - blue)  * t,
    alpha = alpha,
)
