package run.koto.ui.components.atoms

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.dp
import run.koto.ui.theme.KotoTheme
import kotlin.math.PI
import kotlin.math.sin

/**
 * Custom wave typing indicator for CH-04.
 *
 * Three dots oscillate on a sine wave with staggered phase offsets,
 * creating a wave-like "someone is typing" animation.
 *
 * Design specs (RESEARCH.md Pattern 7):
 * - Dot size:  6dp
 * - Amplitude: 4dp vertical offset
 * - Speed:     1000ms full cycle (LinearEasing)
 * - Spacing:   4dp between dots
 * - Phases:    0, 2π/3, 4π/3 (staggered thirds)
 *
 * IMPORTANT: Scope the InfiniteTransition to this composable (not screen level).
 * Wrap with `if (isTyping) { item(...) { TypingWaveIndicator(...) } }` in LazyColumn
 * so the transition is disposed when the item is removed from composition.
 * See RESEARCH.md Pitfall 3 and PITFALLS.md A4.
 */
@Composable
fun TypingWaveIndicator(
    modifier: Modifier = Modifier,
) {
    val colors = KotoTheme.colors

    // InfiniteTransition is disposed when this composable leaves composition — correct behaviour
    val transition = rememberInfiniteTransition(label = "typing_wave")

    val time by transition.animateFloat(
        initialValue  = 0f,
        targetValue   = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
        ),
        label = "wave_time",
    )

    // Phase offsets for 3-dot wave: 0, 120°, 240°
    val phases = listOf(
        0f,
        (2 * PI / 3).toFloat(),
        (4 * PI / 3).toFloat(),
    )

    Box(
        modifier = modifier
            .clip(KotoTheme.shapes.md)
            .background(colors.bubbleIn)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            phases.forEach { phase ->
                val offsetY = sin(time + phase) * 4f   // 4dp amplitude in dp
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .offset(y = offsetY.dp)         // lambda-based overload defers to layout phase
                        .clip(CircleShape)
                        .background(colors.onSurfaceLow),
                )
            }
        }
    }
}
