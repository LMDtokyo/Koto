package run.koto.desktop.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing scale derived from the mockup's inline `padding`/`gap`/`marginXX` values.
 * Clusters tightly around 4 / 8 / 12 / 16 with denser steps in the 4-14 range where
 * the mockup actually has lots of design decisions.
 */
@Immutable
data class KotoSpacing(
    val xxs  : Dp = 2.dp,
    val xs   : Dp = 4.dp,
    val sm   : Dp = 6.dp,
    val md   : Dp = 8.dp,
    val lg   : Dp = 10.dp,
    val xl   : Dp = 12.dp,
    val xxl  : Dp = 14.dp,
    val xxxl : Dp = 16.dp,
    val s20  : Dp = 20.dp,
    val s24  : Dp = 24.dp,
    val s28  : Dp = 28.dp,
    val s32  : Dp = 32.dp,
    val s48  : Dp = 48.dp,

    // ── Layout-specific named tokens ─────────────────────────────────────────
    /** Horizontal padding for screen content (matches mockup's 16-20 px). */
    val screenHorizontal : Dp = 16.dp,
    /** ChatList row vertical padding. */
    val rowVertical      : Dp = 10.dp,
    /** Bubble outer padding (14-16 px → we pick 14 for tighter density). */
    val bubblePaddingH   : Dp = 14.dp,
    val bubblePaddingV   : Dp = 10.dp,
    /** Message list gap (gap between bubbles). */
    val bubbleGap        : Dp = 4.dp,
    /** Gap between bubble groups (different sender or time break). */
    val bubbleGroupGap   : Dp = 14.dp,
    /** Composer inner horizontal padding. */
    val composerInlinePadding : Dp = 12.dp,
)

val DefaultKotoSpacing = KotoSpacing()
