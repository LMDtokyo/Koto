package run.koto.desktop.ui.components.atoms

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class MessageStatus { SENDING, SENT, DELIVERED, READ }

/**
 * Message delivery indicator — port of `Status` from `common.jsx`.
 *
 *   SENDING   — clock outline
 *   SENT      — single check
 *   DELIVERED — double check (dim)
 *   READ      — double check (bright / accent)
 *
 * Size defaults to 14 dp (fits inside a bubble timestamp row).
 */
@Composable
fun StatusIcon(
    status   : MessageStatus,
    color    : Color,
    modifier : Modifier = Modifier,
    size     : Dp      = 14.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        when (status) {
            MessageStatus.SENDING -> drawClock(s, color)
            MessageStatus.SENT    -> drawSingleCheck(s, color)
            MessageStatus.DELIVERED,
            MessageStatus.READ    -> drawDoubleCheck(s, color)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawClock(s: Float, color: Color) {
    val stroke = Stroke(width = s * 0.10f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawCircle(color = color, radius = s * 0.40f, center = Offset(s / 2, s / 2), style = stroke)
    val path = Path().apply {
        moveTo(s / 2, s * 0.29f)
        lineTo(s / 2, s * 0.50f)
        lineTo(s * 0.68f, s * 0.61f)
    }
    drawPath(path, color, style = stroke)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSingleCheck(s: Float, color: Color) {
    val stroke = Stroke(width = s * 0.13f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val path = Path().apply {
        moveTo(s * 0.14f, s * 0.54f)
        lineTo(s * 0.36f, s * 0.75f)
        lineTo(s * 0.86f, s * 0.25f)
    }
    drawPath(path, color, style = stroke)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDoubleCheck(s: Float, color: Color) {
    val stroke = Stroke(width = s * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val p1 = Path().apply {
        moveTo(s * 0.06f, s * 0.55f)
        lineTo(s * 0.25f, s * 0.78f)
        lineTo(s * 0.60f, s * 0.32f)
    }
    val p2 = Path().apply {
        moveTo(s * 0.44f, s * 0.78f)
        lineTo(s * 0.52f, s * 0.70f)
        moveTo(s * 0.56f, s * 0.65f)
        lineTo(s * 0.98f, s * 0.10f)
    }
    drawPath(p1, color, style = stroke)
    drawPath(p2, color, style = stroke)
}
