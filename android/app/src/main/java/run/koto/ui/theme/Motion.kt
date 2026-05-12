package run.koto.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable

/**
 * Motion design tokens for KotoTheme.
 * Access via KotoTheme.motion.* — no composable should use raw spring() or tween() calls
 * with numeric values. All animation specs are named and centralized here.
 *
 * FEATURES.md motion table (source of truth):
 *   - springMicro      : 150-200ms, stiffness=400, dampingRatio=0.70 — icon morphs, tiny state changes
 *   - springSnappy     : standard screen transitions, stiffness=MediumLow, no bounce
 *   - springBouncy     : elastic entrances, stiffness=Medium, low bounce
 *   - springGentle     : soft exits / overlays, stiffness=Low, no bounce
 *   - springEmphasized : 400-500ms, stiffness=200, dampingRatio=0.80 — modal/sheet entry
 *
 * Anti-pattern: spring(dampingRatio = 0.7f, stiffness = 300f) inline in composable.
 * Correct:       KotoTheme.motion.springSnappy
 */
@Immutable
data class KotoMotion(
    // ── Spring specs (physics-based — use for gesture responses, element entrances) ──
    val springSnappy : SpringSpec<Float> = spring(
        stiffness    = Spring.StiffnessMediumLow,
        dampingRatio = Spring.DampingRatioNoBouncy,
    ),
    val springBouncy : SpringSpec<Float> = spring(
        stiffness    = Spring.StiffnessMedium,
        dampingRatio = Spring.DampingRatioLowBouncy,
    ),
    val springGentle : SpringSpec<Float> = spring(
        stiffness    = Spring.StiffnessLow,
        dampingRatio = Spring.DampingRatioNoBouncy,
    ),
    /** FEATURES.md micro tier — icon morphs, reaction micro-animations (150-200ms equivalent). */
    val springMicro      : SpringSpec<Float> = spring(
        stiffness    = 400f,
        dampingRatio = 0.70f,
    ),
    /** FEATURES.md emphasized tier — modal bottom sheet entry, full-screen transitions (400-500ms equivalent). */
    val springEmphasized : SpringSpec<Float> = spring(
        stiffness    = 200f,
        dampingRatio = 0.80f,
    ),
    // ── Tween specs (duration-based — use for explicit UI state transitions) ───────
    val tweenFast   : TweenSpec<Float> = tween(
        durationMillis = 150,
        easing         = FastOutSlowInEasing,
    ),
    val tweenMedium : TweenSpec<Float> = tween(
        durationMillis = 300,
        easing         = FastOutSlowInEasing,
    ),
    val tweenSlow   : TweenSpec<Float> = tween(
        durationMillis = 450,
        easing         = LinearOutSlowInEasing,
    ),
)

/**
 * Default KotoMotion instance — provided via LocalKotoMotion in KotoTheme.
 */
val DefaultKotoMotion = KotoMotion()
