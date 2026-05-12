package run.koto.desktop.ui.screens.chatlist

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import run.koto.desktop.ui.theme.KotoTheme

/**
 * Signal/Mail-style swipeable list row with two action trays.
 *
 * Gestures (mouse-drag on desktop, touch on mobile):
 *   drag right ≥ SNAP_BUFFER_DP → reveals [leftActions] tray
 *   drag left  ≥ SNAP_BUFFER_DP → reveals [rightActions] tray
 *   drag past [FULL_SWIPE_THRESHOLD_DP] then release → the first `commitOnFull`
 *     action in that direction fires, then the row snaps back to closed
 *   drag less than half a tray, release → snaps back to closed
 *   tap while closed → [onOpen]
 *   tap while tray open → snap back to closed (matches the mockup)
 *
 * Implementation: single [Animatable] driving horizontal translation, owned by
 * `detectHorizontalDragGestures` so the drag-end commit semantics we need — and
 * which `AnchoredDraggableState` does not expose — are straightforward.
 *
 * [openRowId] is a shared mutation map so one row opening auto-closes any other —
 * the mockup's `setOpenRow(id)` contract. When the value for this row's [id] is
 * cleared elsewhere, this row animates itself closed.
 */
@Composable
fun SwipeRow(
    id            : String,
    leftActions   : List<SwipeAction>,
    rightActions  : List<SwipeAction>,
    openRowId     : SnapshotStateMap<String, SwipeRowAnchor>,
    onOpen        : () -> Unit,
    modifier      : Modifier = Modifier,
    rowBackground : Color    = KotoTheme.colors.background,
    rowHeight     : Dp       = 72.dp,
    content       : @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val scope   = rememberCoroutineScope()
    val motion  = KotoTheme.motion

    val leftW   = (ACTION_WIDTH_DP * leftActions.size).dp
    val rightW  = (ACTION_WIDTH_DP * rightActions.size).dp
    val leftPx  = with(density) { leftW.toPx() }
    val rightPx = with(density) { rightW.toPx() }
    val fullPx  = with(density) { FULL_SWIPE_THRESHOLD_DP.dp.toPx() }
    val snapBuf = with(density) { SNAP_BUFFER_DP.dp.toPx() }
    val bandPx  = with(density) { 60.dp.toPx() }

    val offset = remember(id) { Animatable(0f) }
    val primaryLeft  = leftActions.firstOrNull  { it.commitOnFull }
    val primaryRight = rightActions.firstOrNull { it.commitOnFull }

    // Another row opening via `openRowId[otherId] = ...` clears this row's entry —
    // watch it and animate closed.
    LaunchedEffect(id) {
        snapshotFlow { openRowId[id] }
            .distinctUntilChanged()
            .collect { anchor ->
                if (anchor == null && offset.value != 0f && !offset.isRunning) {
                    offset.animateTo(0f, motion.springSnappy)
                }
            }
    }

    val absPx     = abs(offset.value)
    val fullLeft  = offset.value >  fullPx && primaryLeft  != null
    val fullRight = offset.value < -fullPx && primaryRight != null
    val leftExpand  by animateDpAsState(if (fullLeft)  402.dp else leftW,  tween(140), label = "sr-expand-l")
    val rightExpand by animateDpAsState(if (fullRight) 402.dp else rightW, tween(140), label = "sr-expand-r")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(rowHeight)
            .background(rowBackground),
    ) {
        // ── Left action tray (reveals by dragging right) ─────────────────────
        if (leftActions.isNotEmpty() && offset.value > 0f) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(leftExpand),
            ) {
                leftActions.forEach { action ->
                    val expand = fullLeft && action.commitOnFull
                    ActionTile(
                        action     = action,
                        modifier   = if (expand) Modifier.weight(1f) else Modifier.width(ACTION_WIDTH_DP.dp),
                        horizontal = if (expand) Alignment.Start else Alignment.CenterHorizontally,
                        onPerform  = {
                            action.onAction()
                            scope.launch { offset.animateTo(0f, motion.springSnappy) }
                            openRowId.remove(id)
                        },
                    )
                }
            }
        }

        // ── Right action tray (reveals by dragging left) ─────────────────────
        if (rightActions.isNotEmpty() && offset.value < 0f) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(rightExpand),
            ) {
                rightActions.forEach { action ->
                    val expand = fullRight && action.commitOnFull
                    ActionTile(
                        action     = action,
                        modifier   = if (expand) Modifier.weight(1f) else Modifier.width(ACTION_WIDTH_DP.dp),
                        horizontal = if (expand) Alignment.End else Alignment.CenterHorizontally,
                        onPerform  = {
                            action.onAction()
                            scope.launch { offset.animateTo(0f, motion.springSnappy) }
                            openRowId.remove(id)
                        },
                    )
                }
            }
        }

        // ── Foreground row — drag + tap ──────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .background(rowBackground)
                .pointerInput(id) {
                    detectHorizontalDragGestures(
                        onDragStart  = { /* open-state updates on settle */ },
                        onDragCancel = { scope.launch { offset.animateTo(0f, motion.springSnappy); openRowId.remove(id) } },
                        onDragEnd    = {
                            scope.launch {
                                resolveDragEnd(
                                    offset       = offset,
                                    value        = offset.value,
                                    leftPx       = leftPx,
                                    rightPx      = rightPx,
                                    fullPx       = fullPx,
                                    snapBuf      = snapBuf,
                                    primaryLeft  = primaryLeft,
                                    primaryRight = primaryRight,
                                    openRowId    = openRowId,
                                    id           = id,
                                    motionSpec   = motion.springSnappy,
                                )
                            }
                        },
                        onHorizontalDrag = { change, dx ->
                            change.consume()
                            val proposed = offset.value + dx
                            val coerced  = proposed.coerceIn(
                                if (rightActions.isEmpty()) 0f else -(rightPx + bandPx),
                                if (leftActions.isEmpty())  0f else  (leftPx  + bandPx),
                            )
                            scope.launch { offset.snapTo(coerced) }
                        },
                    )
                }
                .clickable(enabled = absPx < 2f) { onOpen() }
                .pointerInput(absPx > 2f) {
                    if (absPx > 2f) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            val up = waitForUpOrCancellation()
                            if (up != null) {
                                scope.launch { offset.animateTo(0f, motion.springSnappy); openRowId.remove(id) }
                            }
                        }
                    }
                },
        ) {
            content()
        }
    }
}

/** Decides where the row lands after release. Mirrors the mockup's `onEnd`. */
private suspend fun resolveDragEnd(
    offset       : Animatable<Float, *>,
    value        : Float,
    leftPx       : Float,
    rightPx      : Float,
    fullPx       : Float,
    snapBuf      : Float,
    primaryLeft  : SwipeAction?,
    primaryRight : SwipeAction?,
    openRowId    : SnapshotStateMap<String, SwipeRowAnchor>,
    id           : String,
    motionSpec   : AnimationSpec<Float>,
) {
    when {
        value > fullPx && primaryLeft != null -> {
            primaryLeft.onAction()
            offset.animateTo(0f, motionSpec); openRowId.remove(id)
        }
        value < -fullPx && primaryRight != null -> {
            primaryRight.onAction()
            offset.animateTo(0f, motionSpec); openRowId.remove(id)
        }
        value > leftPx / 2f + snapBuf -> {
            offset.animateTo(leftPx, motionSpec); openRowId[id] = SwipeRowAnchor.LeftOpen
        }
        value < -(rightPx / 2f + snapBuf) -> {
            offset.animateTo(-rightPx, motionSpec); openRowId[id] = SwipeRowAnchor.RightOpen
        }
        else -> {
            offset.animateTo(0f, motionSpec); openRowId.remove(id)
        }
    }
}

/** Action rendered inside a tray. */
data class SwipeAction(
    val id           : String,
    val background   : Color,
    val foreground   : Color,
    val icon         : ImageVector,
    val label        : String,
    val commitOnFull : Boolean = false,
    val onAction     : () -> Unit,
)

/** Anchor the row can settle at. */
enum class SwipeRowAnchor { LeftOpen, Closed, RightOpen }

@Composable
private fun ActionTile(
    action     : SwipeAction,
    modifier   : Modifier,
    horizontal : Alignment.Horizontal,
    onPerform  : () -> Unit,
) {
    Column(
        modifier            = modifier
            .fillMaxHeight()
            .background(action.background)
            .clickable(onClick = onPerform)
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = horizontal,
    ) {
        Icon(imageVector = action.icon, contentDescription = action.label, tint = action.foreground)
        Spacer(Modifier.height(4.dp))
        Text(
            text  = action.label,
            style = KotoTheme.typography.labelSmall,
            color = action.foreground,
        )
    }
}

private const val ACTION_WIDTH_DP          = 74f
private const val FULL_SWIPE_THRESHOLD_DP  = 180f
private const val SNAP_BUFFER_DP           = 14f
