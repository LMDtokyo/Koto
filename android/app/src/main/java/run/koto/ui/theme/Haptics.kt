package run.koto.ui.theme

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * KotoHaptics — centralized haptic feedback utility.
 *
 * Instance-based wrapper around a View reference — create via [rememberKotoHaptics] in Compose.
 *
 * Maps 7 named actions to distinct haptic patterns:
 *   onSend()            — message sent (CONFIRM on API 30+, CONTEXT_CLICK fallback)
 *   onReceive()         — message received (CLOCK_TICK — subtle)
 *   onLongPress()       — long-press context menu (LONG_PRESS)
 *   onSwipeThreshold()  — swipe action crosses commit threshold (CONTEXT_CLICK)
 *   onPullToRefresh()   — pull-to-refresh trigger (CONTEXT_CLICK)
 *   onDoubleTap()       — double-tap reaction (CLOCK_TICK)
 *   onReactionSettle()  — heart reaction settles at 100% scale (CONFIRM API 30+, CONTEXT_CLICK fallback)
 *
 * All methods degrade gracefully on API < 30 using standard View haptics.
 */
class KotoHaptics(private val view: View) {

    /** Message sent — crisp confirmation pulse. */
    fun onSend() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
    }

    /** Message received — subtle tick. */
    fun onReceive() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** Long-press on bubble — standard long-press haptic. */
    fun onLongPress() {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    /** Swipe action crosses commit threshold. */
    fun onSwipeThreshold() {
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    /** Pull-to-refresh trigger point. */
    fun onPullToRefresh() {
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    /** Double-tap reaction — subtle tick to acknowledge gesture. */
    fun onDoubleTap() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** Heart reaction settles at 100% scale — crisp confirmation. */
    fun onReactionSettle() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
    }
}

/**
 * Creates and remembers a [KotoHaptics] instance bound to the current composition's View.
 * Use this at the top of any Composable that needs haptic feedback.
 *
 * Example:
 *   val haptics = rememberKotoHaptics()
 *   haptics.onSend()
 */
@Composable
fun rememberKotoHaptics(): KotoHaptics {
    val view = LocalView.current
    return remember(view) { KotoHaptics(view) }
}
