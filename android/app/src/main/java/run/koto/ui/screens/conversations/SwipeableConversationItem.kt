package run.koto.ui.screens.conversations

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import run.koto.domain.model.ConversationUi
import run.koto.ui.theme.KotoTheme

// ─── Swipe anchor positions ──────────────────────────────────────────────────

private enum class SwipeAnchor { Center, Left, Right }

// ─── SwipeableConversationItem (CL-02) ───────────────────────────────────────

/**
 * Wraps [ConversationItem] with a horizontal swipe layer that reveals action panels.
 *
 * - Swipe left  → teal archive panel at trailing edge
 * - Swipe right → amber pin + gray mute panels at leading edge
 * - Haptic fires at 50% threshold crossing and on commit (snap to anchor)
 * - Offset uses layout-phase lambda form (Modifier.offset { IntOffset(...) }) to avoid
 *   promoting the state read to the composition phase (PITFALLS P3)
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SwipeableConversationItem(
    conv                    : ConversationUi,
    onClick                 : () -> Unit,
    onArchive               : (String) -> Unit,
    onPin                   : (String) -> Unit,
    onMute                  : (String) -> Unit,
    modifier                : Modifier = Modifier,
    sharedTransitionScope   : SharedTransitionScope? = null,
    animatedVisibilityScope : AnimatedVisibilityScope? = null,
) {
    val density = LocalDensity.current
    val view    = LocalView.current
    val colors  = KotoTheme.colors

    val actionWidthPx = with(density) { 72.dp.toPx() }

    val anchors = remember(actionWidthPx) {
        DraggableAnchors {
            SwipeAnchor.Center at 0f
            SwipeAnchor.Left   at -actionWidthPx
            SwipeAnchor.Right  at  actionWidthPx
        }
    }

    val draggableState = remember {
        AnchoredDraggableState(
            initialValue        = SwipeAnchor.Center,
            anchors             = anchors,
            positionalThreshold = { d -> d * 0.5f },
            velocityThreshold   = { with(density) { 125.dp.toPx() } },
            snapAnimationSpec   = spring(
                stiffness    = Spring.StiffnessMedium,
                dampingRatio = Spring.DampingRatioNoBouncy,
            ),
            decayAnimationSpec  = exponentialDecay(),
        )
    }

    // Haptic: fire once per 50% threshold crossing (guards re-fire with hapticFired flag)
    val hapticFired = remember { mutableStateOf(false) }
    val offset = draggableState.offset
    LaunchedEffect(offset) {
        val crossed = kotlin.math.abs(offset) >= actionWidthPx * 0.5f
        if (crossed && !hapticFired.value) {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            hapticFired.value = true
        } else if (!crossed) {
            hapticFired.value = false
        }
    }

    // Commit haptic: fires when item snaps to a non-center anchor
    LaunchedEffect(draggableState.currentValue) {
        if (draggableState.currentValue != SwipeAnchor.Center) {
            val feedbackType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                HapticFeedbackConstants.CONFIRM
            else
                HapticFeedbackConstants.LONG_PRESS
            view.performHapticFeedback(feedbackType)
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        // Behind layer: action backgrounds revealed by the swipe
        SwipeActionBackground(
            offset     = offset,
            onArchive  = { onArchive(conv.id) },
            onPin      = { onPin(conv.id) },
            onMute     = { onMute(conv.id) },
            errorColor = colors.error,
        )

        // Front layer: conversation content offset by drag state
        // Modifier.offset { IntOffset } defers state read to layout phase (PITFALLS P3)
        ConversationItem(
            conv                    = conv,
            onClick                 = onClick,
            modifier                = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offset.roundToInt(), 0) }
                .anchoredDraggable(draggableState, Orientation.Horizontal),
            sharedTransitionScope   = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
        )
    }
}

// ─── SwipeActionBackground ────────────────────────────────────────────────────

@Composable
private fun SwipeActionBackground(
    offset     : Float,
    onArchive  : () -> Unit,
    onPin      : () -> Unit,
    onMute     : () -> Unit,
    @Suppress("UNUSED_PARAMETER") errorColor: Color,  // reserved for future delete action
) {
    // Gesture-action colors — not brand tokens, intentionally local to this composable
    val archiveTeal = Color(0xFF0D9488)
    val pinAmber    = Color(0xFFF59E0B)
    val muteGray    = Color(0xFF6B7280)

    Box(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
        when {
            offset < 0f -> {
                // Left swipe: archive action on the trailing (right) side
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(72.dp)
                        .align(Alignment.CenterEnd)
                        .background(archiveTeal)
                        .clickable { onArchive() },
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector        = Icons.Default.Archive,
                        contentDescription = "Archive",
                        tint               = Color.White,
                        modifier           = Modifier.size(24.dp),
                    )
                }
            }
            offset > 0f -> {
                // Right swipe: pin + mute actions on the leading (left) side
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(144.dp)
                        .align(Alignment.CenterStart),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                            .background(pinAmber)
                            .clickable { onPin() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector        = Icons.Default.PushPin,
                            contentDescription = "Pin",
                            tint               = Color.White,
                            modifier           = Modifier.size(24.dp),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                            .background(muteGray)
                            .clickable { onMute() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector        = Icons.Default.VolumeOff,
                            contentDescription = "Mute",
                            tint               = Color.White,
                            modifier           = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}
