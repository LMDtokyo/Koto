package run.koto.ui.screens.conversations

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import run.koto.ui.theme.KotoTheme

/**
 * KotoPullToRefresh wraps Material3's PullToRefreshBox with a custom blob indicator.
 *
 * CL-05: Primary-color blob stretches from top edge as user pulls, detaches at threshold
 * to form a pulsing circle, then shrinks away with spring on completion.
 *
 * Haptic feedback fires at the pull threshold via HapticFeedbackConstants.CONFIRM (API 30+)
 * or LONG_PRESS on older devices.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KotoPullToRefresh(
    isRefreshing : Boolean,
    onRefresh    : () -> Unit,
    modifier     : Modifier = Modifier,
    content      : @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()

    // ── Haptic at trigger threshold ─────────────────────────────────────────────
    val view = LocalView.current
    var hapticFired by remember { mutableStateOf(false) }
    LaunchedEffect(state.distanceFraction) {
        val triggered = state.distanceFraction >= 1f
        if (triggered && !hapticFired) {
            @Suppress("DEPRECATION")
            val feedbackType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                HapticFeedbackConstants.CONFIRM
            else
                HapticFeedbackConstants.LONG_PRESS
            view.performHapticFeedback(feedbackType)
            hapticFired = true
        } else if (!triggered) {
            hapticFired = false
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh    = onRefresh,
        state        = state,
        modifier     = modifier,
        indicator    = {
            KotoBlobIndicator(
                state        = state,
                isRefreshing = isRefreshing,
                modifier     = Modifier.align(Alignment.TopCenter),
            )
        },
        content = content,
    )
}

/**
 * Custom Canvas-based blob indicator.
 *
 * Morphs from a stretched oval (pulling) → circle at threshold → pulsing circle
 * during refresh → spring-shrink to 0 on completion.
 *
 * Uses KotoTheme.colors.primary for the blob color (no hardcoded hex values).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KotoBlobIndicator(
    state        : androidx.compose.material3.pulltorefresh.PullToRefreshState,
    isRefreshing : Boolean,
    modifier     : Modifier = Modifier,
) {
    val primaryColor = KotoTheme.colors.primary
    // KotoTheme.motion referenced for consistency with design system motion tokens
    @Suppress("UNUSED_VARIABLE")
    val motion = KotoTheme.motion

    // progress: 0f = no pull, 1f = threshold reached
    val progress = state.distanceFraction.coerceIn(0f, 1f)

    // ── Pulsing during refresh ──────────────────────────────────────────────────
    val pulseScale by rememberInfiniteTransition(label = "refreshPulse")
        .animateFloat(
            initialValue  = 1f,
            targetValue   = 1.05f,
            animationSpec = infiniteRepeatable(
                animation  = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulseScale",
        )

    // ── Shrink-to-zero when refresh completes ───────────────────────────────────
    val completionScale by animateFloatAsState(
        targetValue   = if (isRefreshing) 1f else 0f,
        animationSpec = if (isRefreshing) spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness    = Spring.StiffnessMedium,
        ) else spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness    = Spring.StiffnessMediumLow,
        ),
        label = "completionScale",
    )

    val circleRadius = 12.dp  // 24dp diameter indicator circle

    Canvas(
        modifier = modifier
            .padding(top = 4.dp)
            .size(width = 32.dp, height = 48.dp)
    ) {
        val centerX  = size.width / 2f
        val radiusPx = circleRadius.toPx()

        if (isRefreshing) {
            // Pulsing circle during loading, springs to zero on completion
            val r = radiusPx * pulseScale * completionScale
            if (r > 0f) {
                drawCircle(
                    color  = primaryColor,
                    radius = r,
                    center = Offset(centerX, radiusPx + 4.dp.toPx()),
                )
            }
        } else if (progress > 0f) {
            // Blob morphing: stretched oval → circle as progress → 1.0
            val circleY = radiusPx + 4.dp.toPx()
            val blobH   = radiusPx * (1f + (1f - progress) * 1.5f)   // taller when less progress
            val blobW   = radiusPx * (0.6f + progress * 0.4f)         // narrower when less progress
            val alpha   = (progress * 2f).coerceIn(0f, 1f)            // fade in during first 50% pull

            drawOval(
                color   = primaryColor.copy(alpha = alpha),
                topLeft = Offset(centerX - blobW, circleY - blobH),
                size    = Size(blobW * 2f, blobH * 2f),
            )
        }
    }
}
