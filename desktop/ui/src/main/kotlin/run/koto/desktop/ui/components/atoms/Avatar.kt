package run.koto.desktop.ui.components.atoms

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import run.koto.desktop.ui.theme.KotoTheme

/**
 * Gradient-fill avatar with optional initials, online dot, and breathing brand pulse.
 *
 * Matches the design mockup's `Avatar` component from `common.jsx`:
 *   - 135° linear gradient from [color] (base) to a darker shade (-18% luminance)
 *   - 28%-of-size green online dot at bottom-right, separated by a 2 dp ring
 *   - `pulse=true` adds an infinite 2 s glow halo matching CSS `pulseAvatar`
 */
@Composable
fun Avatar(
    initials : String,
    color    : Color,
    modifier : Modifier = Modifier,
    size     : Dp      = 48.dp,
    online   : Boolean = false,
    pulse    : Boolean = false,
) {
    val gradient = remember(color) { Brush.linearGradient(colors = listOf(color, color.shade(-0.18f))) }

    // Infinite halo drives the haloRadiusPx and haloAlpha only when pulse=true.
    val pulseDurationMs = KotoTheme.motion.pulseAvatarDurationMillis
    val transition      = rememberInfiniteTransition(label = "avatar-pulse")
    val haloPhase by transition.animateFloat(
        initialValue  = 0f,
        targetValue   = if (pulse) 1f else 0f,
        animationSpec = infiniteRepeatable(tween(pulseDurationMs), RepeatMode.Reverse),
        label         = "avatar-halo-phase",
    )
    val haloColor = KotoTheme.colors.accentGlow
    val backgroundColor = KotoTheme.colors.background
    val successColor = KotoTheme.colors.success

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        // Halo behind the disk — only paints when pulse is actually on.
        if (pulse) {
            Box(
                modifier = Modifier
                    .size(size)
                    .drawBehind {
                        val r = this.size.minDimension / 2f + 14f * haloPhase
                        drawCircle(
                            color  = haloColor.copy(alpha = haloColor.alpha * (1f - haloPhase)),
                            radius = r,
                        )
                    },
            )
        }

        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(gradient),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text       = initials,
                color      = Color.White,
                fontSize   = (size.value * 0.4f).sp,
                fontWeight = FontWeight.SemiBold,
                textAlign  = TextAlign.Center,
            )
        }

        if (online) {
            val dotSize = size * 0.28f
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(successColor)
                    .border(2.dp, backgroundColor, CircleShape),
            )
        }
    }
}

/** Darkens or lightens a color by a percentage. `pct < 0` darker, `pct > 0` lighter. */
private fun Color.shade(pct: Float): Color {
    val target = if (pct < 0f) 0f else 1f
    val p = kotlin.math.abs(pct).coerceIn(0f, 1f)
    return Color(
        red   = red   + (target - red)   * p,
        green = green + (target - green) * p,
        blue  = blue  + (target - blue)  * p,
        alpha = alpha,
    )
}

/**
 * Deterministic color derivation from an id string — useful when a contact has no
 * specific brand color. 10-hue palette tuned to complement the Koto orange.
 */
fun avatarColorFor(id: String): Color {
    val idx = kotlin.math.abs(id.hashCode()) % AVATAR_COLORS.size
    return AVATAR_COLORS[idx]
}

private val AVATAR_COLORS = listOf(
    Color(0xFFFF6B35),      // accent
    Color(0xFF0EA5E9),      // sky
    Color(0xFF10B981),      // emerald
    Color(0xFFEC4899),      // pink
    Color(0xFF8B5CF6),      // violet
    Color(0xFFF59E0B),      // amber
    Color(0xFF22D3EE),      // cyan
    Color(0xFFA3E635),      // lime
    Color(0xFFEF4444),      // red
    Color(0xFF6366F1),      // indigo
)
