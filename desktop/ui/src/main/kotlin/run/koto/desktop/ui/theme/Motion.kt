package run.koto.desktop.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable

/**
 * Motion tokens — every CSS keyframe in the design mockup has a named Compose equivalent
 * here. Named constants only: callers MUST NOT write `spring()` / `tween()` with raw numeric
 * literals in composables.
 *
 * Signature easing `cubic-bezier(.2, .8, .2, 1)` mirrors the mockup's go-to curve for
 * snap interactions (SwipeRow release, push/pop screen transitions, sheet dismiss).
 */

/** Signature Koto easing — matches CSS `cubic-bezier(.2,.8,.2,1)`. */
val KotoEasing: androidx.compose.animation.core.Easing = CubicBezierEasing(.2f, .8f, .2f, 1f)

@Immutable
data class KotoMotion(
    // ── Springs (physics) ────────────────────────────────────────────────────
    /** Standard element transitions — snap but no overshoot. */
    val springSnappy     : SpringSpec<Float> = spring(
        stiffness    = Spring.StiffnessMediumLow,
        dampingRatio = Spring.DampingRatioNoBouncy,
    ),

    /** Elastic entrances — message send, reaction pop, chip appear. */
    val springBouncy     : SpringSpec<Float> = spring(
        stiffness    = Spring.StiffnessMedium,
        dampingRatio = Spring.DampingRatioLowBouncy,
    ),

    /** Soft overlays — popup scrim, dropdown fade. */
    val springGentle     : SpringSpec<Float> = spring(
        stiffness    = Spring.StiffnessLow,
        dampingRatio = Spring.DampingRatioNoBouncy,
    ),

    /** Icon morphs — send button state change, status flip (15-200 ms). */
    val springMicro      : SpringSpec<Float> = spring(
        stiffness    = 400f,
        dampingRatio = 0.70f,
    ),

    /** Modals / full-screen sheet entry — 400-500 ms feel. */
    val springEmphasized : SpringSpec<Float> = spring(
        stiffness    = 200f,
        dampingRatio = 0.80f,
    ),

    // ── Tweens (duration-based) ──────────────────────────────────────────────
    /** CSS `fadeScaleIn` — 560 ms, gentle in. */
    val tweenFadeScale    : TweenSpec<Float> = tween(560, easing = KotoEasing),

    /** CSS `bubblePop` — 260 ms, elastic lift of a new message. */
    val tweenBubblePop    : TweenSpec<Float> = tween(260, easing = KotoEasing),

    /** CSS `slideIn/OutRight|Left` — 320 ms iOS screen push/pop. */
    val tweenSlidePushPop : TweenSpec<Float> = tween(320, easing = KotoEasing),

    /** CSS `sheetUp` / `overlayIn` — bottom-sheet slide. */
    val tweenSheet        : TweenSpec<Float> = tween(320, easing = KotoEasing),

    /** Generic fast — 150 ms for low-effort state flips (press, hover). */
    val tweenFast         : TweenSpec<Float> = tween(150, easing = FastOutSlowInEasing),

    val tweenMedium       : TweenSpec<Float> = tween(300, easing = FastOutSlowInEasing),
    val tweenSlow         : TweenSpec<Float> = tween(450, easing = LinearOutSlowInEasing),

    // ── Infinite loops (ambient) ─────────────────────────────────────────────
    /** CSS `dot` — 1200 ms per typing dot cycle, 0.18 s stagger. */
    val typingDotDurationMillis : Int = 1200,

    /** CSS `pulseRing` — 2200 ms call avatar halo. */
    val pulseRingDurationMillis : Int = 2200,

    /** CSS `pulseAvatar` — 2000 ms brand-glow breathing. */
    val pulseAvatarDurationMillis : Int = 2000,

    /** CSS `auroraDrift` — 16 000 ms slow background drift on Call screen. */
    val auroraDriftDurationMillis : Int = 16_000,

    /** CSS `shimmer` — 2000 ms moving gradient on "encryption establishing" text. */
    val shimmerDurationMillis : Int = 2000,

    /** CSS `orbit` — 360° rotation, 3500 ms. */
    val orbitDurationMillis : Int = 3500,

    // ── Long-press reaction picker (Conversation screen) ────────────────────
    /** CSS `reactPickerIn` — picker scale+bounce on long-press open. */
    val reactPicker : SpringSpec<Float> = spring(stiffness = 380f, dampingRatio = 0.55f),

    /** CSS `emojiPop` — individual emoji drop into reaction chip. */
    val emojiPop : SpringSpec<Float> = spring(stiffness = 500f, dampingRatio = 0.50f),
)

/** Default instance — provisioned as `KotoTheme.motion` via a CompositionLocal. */
val DefaultKotoMotion = KotoMotion()
