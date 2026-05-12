package run.koto.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import run.koto.ui.theme.AccentPrimary
import kotlin.math.roundToInt

// ─── Dimensions ───────────────────────────────────────────────────────────────
private val TrackWidth    = 50.dp
private val TrackHeight   = 30.dp
private val ThumbSize     = 26.dp
private val ThumbPadding  = 2.dp                            // gap between thumb and track edge
// Max travel = TrackWidth - ThumbSize - ThumbPadding * 2 = 20dp

// ─── Colors ───────────────────────────────────────────────────────────────────
private val TrackOff  = Color(0xFF3A3A3C)   // iOS off-state gray
private val ThumbColor = Color.White

/**
 * Professional custom toggle switch.
 *
 * Why not Material3 Switch:
 *   Material3 bakes in its own animation spec and doesn't expose a way to
 *   customise duration or easing from outside. This composable uses:
 *
 *   • Thumb position  — spring(NoBouncy, StiffnessMediumLow)
 *                       Physics-based; handles rapid repeated taps without
 *                       discontinuous jumps (velocity is preserved on interrupt).
 *
 *   • Track colour    — tween(200ms) synced closely with the spring duration.
 *                       A fast fade looks cleaner than an abrupt cut.
 *
 *   • Thumb shadow    — 2dp elevation so the thumb appears "lifted" off the track,
 *                       matching iOS and Telegram's depth cue.
 */
@Composable
fun KotoSwitch(
    checked          : Boolean,
    onCheckedChange  : (Boolean) -> Unit,
    modifier         : Modifier = Modifier,
    enabled          : Boolean  = true,
) {
    val density = LocalDensity.current

    // ── Thumb animation ───────────────────────────────────────────────────────
    // Fraction 0f = off (left), 1f = on (right).
    // spring NoBouncy + StiffnessMediumLow → smooth ~220ms, no overshoot.
    val thumbFraction by animateFloatAsState(
        targetValue   = if (checked) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness    = Spring.StiffnessMediumLow,
        ),
        label = "thumbFraction",
    )

    // ── Track colour animation ────────────────────────────────────────────────
    val trackColor by animateColorAsState(
        targetValue   = if (checked) AccentPrimary else TrackOff,
        animationSpec = tween(durationMillis = 200),
        label         = "trackColor",
    )

    // Convert to pixels once outside the draw pass (stable across recompositions)
    val thumbPaddingPx = with(density) { ThumbPadding.toPx() }
    val thumbTravelPx  = with(density) { (TrackWidth - ThumbSize - ThumbPadding * 2).toPx() }

    Box(
        modifier = modifier
            .size(TrackWidth, TrackHeight)
            .clip(RoundedCornerShape(TrackHeight / 2))
            .background(trackColor)
            .toggleable(
                value             = checked,
                onValueChange     = { if (enabled) onCheckedChange(it) },
                role              = Role.Switch,
                enabled           = enabled,
                indication        = null,                       // no ripple on track
                interactionSource = remember { MutableInteractionSource() },
            ),
    ) {
        // Thumb
        Box(
            modifier = Modifier
                .padding(top = ThumbPadding)                    // vertical centering
                .offset {
                    IntOffset(
                        x = (thumbPaddingPx + thumbFraction * thumbTravelPx).roundToInt(),
                        y = 0,
                    )
                }
                .size(ThumbSize)
                .shadow(
                    elevation    = 2.dp,
                    shape        = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.3f),
                    spotColor    = Color.Black.copy(alpha = 0.3f),
                )
                .clip(CircleShape)
                .background(ThumbColor),
        )
    }
}
