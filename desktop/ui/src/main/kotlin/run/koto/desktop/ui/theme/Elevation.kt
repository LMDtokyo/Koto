package run.koto.desktop.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Elevation tokens — mockup uses soft, iOS-style shadows. On desktop CMP we translate to
 * Compose `shadow()` dp values. Real-world measurements (from `box-shadow` strings):
 *   light: `0 10px 30px rgba(0,0,0,0.08)` ≈ elevation 2-3
 *   dark : `0 16px 48px rgba(0,0,0,0.4)` ≈ elevation 4-6
 */
@Immutable
data class KotoElevation(
    val none    : Dp = 0.dp,
    val xs      : Dp = 1.dp,   // pressed / low separation
    val sm      : Dp = 2.dp,   // default card
    val md      : Dp = 4.dp,   // floating action button
    val lg      : Dp = 8.dp,   // bottom sheet, modal dialog
    val xl      : Dp = 16.dp,  // call screen controls, raised bars
)

val DefaultKotoElevation = KotoElevation()
