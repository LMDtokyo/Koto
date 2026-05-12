package run.koto.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Shape design tokens for KotoTheme.
 * Access via KotoTheme.shapes.* — never hardcode corner radii in composables.
 *
 * DS-05: sm=8dp, md=12dp, lg=16dp, xl=20dp, bubble outer corners=18dp.
 * Chat bubble shapes are asymmetric: the tail-adjacent corner is 4dp (inner corner),
 * outer corners are 18dp. bubbleOut = outgoing (tail bottom-right), bubbleIn = incoming.
 */
@Immutable
data class KotoShapes(
    // ── Chat bubbles (asymmetric — tail corner = 4dp per FEATURES.md) ─────────
    // RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)
    val bubbleOut  : Shape = RoundedCornerShape(18.dp, 18.dp,  4.dp, 18.dp),  // tail bottom-right
    val bubbleIn   : Shape = RoundedCornerShape(18.dp, 18.dp, 18.dp,  4.dp),  // tail bottom-left
    // ── Standard shapes (DS-05) ──────────────────────────────────────────────
    val sm         : Shape = RoundedCornerShape( 8.dp),   // input fields, chips
    val md         : Shape = RoundedCornerShape(12.dp),   // media thumbnails, context menus
    val lg         : Shape = RoundedCornerShape(16.dp),   // cards, nav elements
    val xl         : Shape = RoundedCornerShape(20.dp),   // modals, large cards
    val bubble     : Shape = RoundedCornerShape(18.dp),   // bubble outer corners (DS-05)
    // ── Messenger-specific ────────────────────────────────────────────────────
    val bottomSheet: Shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    val inputField : Shape = RoundedCornerShape(24.dp),   // pill compose bar
    val pill       : Shape = CircleShape,                 // avatars, badges, FAB
    val card       : Shape = RoundedCornerShape(16.dp),   // floating cards
)
