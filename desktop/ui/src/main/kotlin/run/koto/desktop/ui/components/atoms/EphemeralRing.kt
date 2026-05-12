package run.koto.desktop.ui.components.atoms

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI

/**
 * Circular progress ring shown on ephemeral messages — port of `EphemeralRing` from
 * `common.jsx`. `pct ∈ [0, 1]` is the fraction remaining of the message's life:
 *
 *   pct = 1.0 → a full ring (just sent)
 *   pct = 0.0 → an empty ring (expires now)
 *
 * Drawn on a 14 dp canvas by default; stroke is 1.5 px constant.
 */
@Composable
fun EphemeralRing(
    pct      : Float,
    color    : Color,
    modifier : Modifier = Modifier,
    size     : Dp      = 14.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val diameter = this.size.minDimension
        val stroke = Stroke(width = 1.5f.dp.toPx(), cap = StrokeCap.Round)
        val inset = stroke.width / 2f
        val radius = diameter / 2f - inset

        // Background track
        drawCircle(
            color  = color.copy(alpha = 0.25f),
            radius = radius,
            style  = stroke,
        )
        // Progress arc — starts at -90° (top), sweeps clockwise
        val sweep = 360f * pct.coerceIn(0f, 1f)
        drawArc(
            color       = color,
            startAngle  = -90f,
            sweepAngle  = sweep,
            useCenter   = false,
            topLeft     = androidx.compose.ui.geometry.Offset(inset, inset),
            size        = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            style       = stroke,
        )
    }
}

private val PI_F = PI.toFloat()
