package run.koto.desktop.ui.chrome

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.roundToInt

private const val BOUNDS_ANIM_MS = 340
private const val MAXIMIZE_EPS_PX = 12

private fun lerpPx(a: Int, b: Int, t: Float): Int = (a + (b - a) * t).roundToInt()

private fun nearlyEqualRect(a: Rectangle, b: Rectangle): Boolean =
    abs(a.x - b.x) <= MAXIMIZE_EPS_PX &&
        abs(a.y - b.y) <= MAXIMIZE_EPS_PX &&
        abs(a.width - b.width) <= MAXIMIZE_EPS_PX &&
        abs(a.height - b.height) <= MAXIMIZE_EPS_PX

private fun workAreaFor(window: java.awt.Window): Rectangle {
    val gc = window.graphicsConfiguration
    val bounds = gc.bounds
    val insets = Toolkit.getDefaultToolkit().getScreenInsets(gc)
    return Rectangle(
        bounds.x + insets.left,
        bounds.y + insets.top,
        bounds.width - insets.left - insets.right,
        bounds.height - insets.top - insets.bottom,
    )
}

private class AwtSyncSlot {
    var block: () -> Unit = {}
}

private fun defaultRestoreRect(work: Rectangle): Rectangle {
    val w = (work.width * 0.72f).roundToInt()
    val h = (work.height * 0.78f).roundToInt()
    val x = work.x + (work.width - w) / 2
    val y = work.y + (work.height - h) / 2
    return Rectangle(x, y, w, h)
}

@Stable
class WindowMaximizeController internal constructor(
    private val window: ComposeWindow,
    private val windowState: WindowState,
    internal val jbrMode: Boolean,
) {
    private val mutex = Mutex()
    private var restoreRect: Rectangle? = null
    private var animDensity: Density? = null

    val boundsAnimationRunning = AtomicBoolean(false)

    val maximizedState: MutableState<Boolean> = mutableStateOf(
        windowState.placement == WindowPlacement.Maximized,
    )

    val isMaximized: Boolean get() = maximizedState.value

    val isJbr: Boolean get() = jbrMode

    fun updateDensity(density: Density) {
        animDensity = density
    }

    /** Match AWT erase color to theme (see DesktopRendering). */
    var awtBackgroundSync: () -> Unit = {}

    suspend fun toggle() = mutex.withLock {
        val density = animDensity ?: return@withLock
        awtBackgroundSync()
        val work = workAreaFor(window)

        if (windowState.placement == WindowPlacement.Maximized) {
            windowState.placement = WindowPlacement.Floating
            delay(1)
            yield()
        }

        val current = Rectangle(window.location, window.size)
        val maxed = maximizedState.value || windowState.placement == WindowPlacement.Maximized

        boundsAnimationRunning.set(true)
        try {
            if (maxed) {
                val end = restoreRect ?: defaultRestoreRect(work)
                animateBounds(density, current, end)
                maximizedState.value = false
                windowState.placement = WindowPlacement.Floating
            } else {
                restoreRect = Rectangle(current)
                animateBounds(density, current, work)
                maximizedState.value = true
                windowState.placement = WindowPlacement.Floating
            }
        } finally {
            boundsAnimationRunning.set(false)
        }
    }

    private suspend fun animateBounds(density: Density, from: Rectangle, to: Rectangle) {
        awtBackgroundSync()
        windowState.placement = WindowPlacement.Floating
        val minW = with(density) { 900.dp.roundToPx() }
        val minH = with(density) { 600.dp.roundToPx() }
        val anim = Animatable(0f)
        anim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = BOUNDS_ANIM_MS, easing = FastOutSlowInEasing),
        ) {
            val t = value
            val x = lerpPx(from.x, to.x, t)
            val y = lerpPx(from.y, to.y, t)
            val w = lerpPx(from.width, to.width, t).coerceAtLeast(minW)
            val h = lerpPx(from.height, to.height, t).coerceAtLeast(minH)
            with(density) {
                windowState.position = WindowPosition.Absolute(x.toDp(), y.toDp())
                windowState.size = DpSize(width = w.toDp(), height = h.toDp())
            }
        }
    }

    internal fun onWindowManuallyResized() {
        if (boundsAnimationRunning.get()) return
        val maxed = maximizedState.value || windowState.placement == WindowPlacement.Maximized
        if (!maxed) return
        val work = workAreaFor(window)
        val cur = window.bounds
        if (!nearlyEqualRect(cur, work)) {
            maximizedState.value = false
            windowState.placement = WindowPlacement.Floating
            restoreRect = Rectangle(cur)
        }
    }
}

@Composable
fun rememberWindowMaximizeController(
    window: ComposeWindow,
    windowState: WindowState,
    titleBarHeight: Dp,
    density: Density,
    onAwtResize: () -> Unit = {},
): WindowMaximizeController {
    val awtSlot = remember { AwtSyncSlot() }
    val controller = remember(window, windowState) {
        val jbrMode = JbrWindowDecorations.tryAttach(window, titleBarHeight, density)
        WindowMaximizeController(window, windowState, jbrMode)
    }
    SideEffect {
        controller.updateDensity(density)
        awtSlot.block = onAwtResize
        controller.awtBackgroundSync = { awtSlot.block.invoke() }
    }
    LaunchedEffect(titleBarHeight, controller.isJbr) {
        if (controller.isJbr) {
            JbrWindowDecorations.updateHeight(window, titleBarHeight, density)
        }
    }
    DisposableEffect(window, windowState, controller) {
        val listener = object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                controller.awtBackgroundSync()
                controller.onWindowManuallyResized()
            }
        }
        window.addComponentListener(listener)
        onDispose { window.removeComponentListener(listener) }
    }
    return controller
}

fun Modifier.maximizeTransform(ctrl: WindowMaximizeController): Modifier = this
