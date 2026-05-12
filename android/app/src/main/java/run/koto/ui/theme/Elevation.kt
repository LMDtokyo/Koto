package run.koto.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Elevation tokens for KotoTheme.
 * Access via KotoTheme.elevation.* — use with Surface(tonalElevation = ...).
 *
 * Levels match FEATURES.md elevation scale table (0dp through 8dp).
 */
@Immutable
data class KotoElevation(
    val none    : Dp =  0.dp,   // flat surfaces, backgrounds
    val low     : Dp =  1.dp,   // cards in lists
    val card    : Dp =  2.dp,   // compose bar, navigation bar
    val appBar  : Dp =  4.dp,   // app bar elevation
    val fab     : Dp =  6.dp,   // FAB elevation
    val dialog  : Dp =  8.dp,   // context menus, dropdowns
    val pressed : Dp =  8.dp,   // interactive press elevation
)
