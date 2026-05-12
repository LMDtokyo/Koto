package run.koto.desktop.ui.components.atoms

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import run.koto.desktop.ui.theme.KotoTheme

/**
 * Three-dot typing indicator. Mirrors CSS keyframe `dot` from the mockup:
 *   0%, 60%, 100% — translateY(0), opacity 0.35
 *   30%          — translateY(-4 px), opacity 1.0
 *
 * Each dot starts 180 ms later than the previous, creating the signature "walking"
 * wave. The duration is read from [KotoTheme.motion.typingDotDurationMillis] so a
 * theme-level tweak flows through automatically.
 */
@Composable
fun TypingDots(
    color    : Color = KotoTheme.colors.accent,
    size     : Dp   = 5.dp,
    modifier : Modifier = Modifier,
) {
    val durationMs = KotoTheme.motion.typingDotDurationMillis
    val transition = rememberInfiniteTransition(label = "typing-dots")

    // Pre-build the keyframes spec once — then re-use for all three dots via StartOffset.
    val spec = infiniteRepeatable<Float>(
        animation = keyframes {
            this.durationMillis = durationMs
            0f   at 0
            1f   at durationMs * 30 / 100          // 30% peak
            0f   at durationMs * 60 / 100          // 60% back to base
            0f   at durationMs
        },
        repeatMode = RepeatMode.Restart,
    )

    Row(
        modifier             = modifier.height(size + 4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment    = Alignment.Bottom,
    ) {
        repeat(3) { i ->
            val phase by transition.animateFloat(
                initialValue = 0f,
                targetValue  = 1f,
                animationSpec = infiniteRepeatable(
                    animation          = spec.animation,
                    repeatMode         = spec.repeatMode,
                    initialStartOffset = StartOffset(i * 180, StartOffsetType.FastForward),
                ),
                label = "dot-$i",
            )
            Box(
                modifier = Modifier
                    .size(size)
                    .offset(y = ((-4f) * phase).dp)
                    .alpha(0.35f + 0.65f * phase)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}
