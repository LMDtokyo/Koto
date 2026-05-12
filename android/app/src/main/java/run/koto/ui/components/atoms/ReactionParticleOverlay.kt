package run.koto.ui.components.atoms

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import run.koto.ui.theme.KotoTheme

/**
 * MI-02: Heart reaction animation triggered by double-tap on a message bubble.
 *
 * Lifecycle (runs once then calls onFinished):
 *   1. Spring scale 0 → 1.0 using KotoTheme.motion.springBouncy (naturally overshoots to ~1.2)
 *   2. While settled at 1.0, float up 24dp and fade out over 600ms (tween)
 *   3. Call onFinished() — caller removes this from composition
 *
 * Also calls haptics callbacks at the correct moments:
 *   - onReactionSettle() fires when the spring settles (heart at 100% scale)
 *
 * MI-05 compliance: springBouncy from KotoTheme.motion — no raw spring() literals.
 * PITFALLS P3: offset reads in layout phase via lambda overload (not composition phase).
 */
@Composable
fun HeartReaction(
    onFinished       : () -> Unit,
    onReactionSettle : () -> Unit,    // caller fires haptics.onReactionSettle()
    modifier         : Modifier = Modifier,
) {
    val motion  = KotoTheme.motion
    val density = LocalDensity.current

    val scale   = remember { Animatable(0f) }
    // offsetY stored in px — avoids Dp→px conversion inside layout lambda
    val offsetY = remember { Animatable(0f) }
    val alpha   = remember { Animatable(1f) }

    // Compute target offset in px once (24dp float up)
    val targetOffsetPx = with(density) { (-24).dp.toPx() }

    LaunchedEffect(Unit) {
        // Phase 1: spring scale 0 → 1.0 (springBouncy overshoots naturally to ~1.2 → settles 1.0)
        scale.animateTo(1f, animationSpec = motion.springBouncy)
        onReactionSettle()  // heart has settled at 100% — fire medium haptic

        // Phase 2: float up 24dp + fade out over 600ms (concurrent)
        // Negative offsetY = moves up (Compose Y axis: positive = down)
        launch {
            offsetY.animateTo(targetOffsetPx, animationSpec = tween(600))
        }
        alpha.animateTo(0f, animationSpec = tween(600))
        onFinished()
    }

    Box(
        modifier = modifier
            // Lambda overload defers read to layout phase — avoids recomposition per frame (PITFALLS P3)
            .offset { IntOffset(0, offsetY.value.roundToInt()) }
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .alpha(alpha.value)
            .size(40.dp),
    ) {
        Text(
            text     = "❤️",
            fontSize = 24.sp,
        )
    }
}
