package run.koto.desktop.ui.components.atoms

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntSize
import run.koto.desktop.ui.theme.KotoTheme

/**
 * Gradient-filled text with a continuously sweeping shimmer — the "encryption
 * establishing" label on the Call screen. A real `Brush.linearGradient` drives the
 * text fill via [TextStyle.brush] (Compose 1.7+ API, ships with CMP 1.10.3), and the
 * gradient's start/end Offsets translate across the layout every [shimmerDurationMillis].
 *
 * Sizing: we don't know the layout width at composition time, so we capture it via
 * [onSizeChanged] and derive the sweep range from it. First frame uses a 200-px stub
 * until the first measurement arrives (invisible because alpha is near zero early on).
 */
@Composable
fun ShimmerText(
    text     : String,
    style    : TextStyle,
    modifier : Modifier = Modifier,
    baseColor: Color = KotoTheme.colors.textSecondary,
    hotColor : Color = KotoTheme.colors.text,
) {
    var measuredWidth by remember { mutableStateOf(0f) }

    val transition = rememberInfiniteTransition(label = "shimmer")
    val phase by transition.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(KotoTheme.motion.shimmerDurationMillis),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer-phase",
    )

    // Shimmer band travels from x = -w to x = 2w across a phase 0..1, so there's
    // always a leading edge approaching and a trailing edge leaving the text box.
    val w = if (measuredWidth > 0f) measuredWidth else 200f
    val leading = -w + phase * 3f * w
    val brush = Brush.linearGradient(
        colors = listOf(baseColor, hotColor, baseColor),
        start  = Offset(leading,        0f),
        end    = Offset(leading + w,    0f),
    )

    Text(
        text     = text,
        style    = style.copy(brush = brush, textAlign = TextAlign.Center),
        modifier = modifier.onSizeChanged { s: IntSize -> measuredWidth = s.width.toFloat() },
    )
}
