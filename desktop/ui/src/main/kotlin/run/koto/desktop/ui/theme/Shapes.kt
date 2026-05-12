package run.koto.desktop.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Shape tokens. Most radii come from `style={{ borderRadius: N }}` in the mockup JSX;
 * they cluster around 12–22 dp with characteristic asymmetric bubble corners.
 */
@Immutable
data class KotoShapes(
    val xs        : Shape = RoundedCornerShape(6.dp),
    val sm        : Shape = RoundedCornerShape(10.dp),
    val md        : Shape = RoundedCornerShape(14.dp),
    val lg        : Shape = RoundedCornerShape(18.dp),
    val xl        : Shape = RoundedCornerShape(22.dp),
    val xxl       : Shape = RoundedCornerShape(28.dp),

    /** 22-dp bubble with a 6-dp "tail" corner on the outgoing side. */
    val bubbleSelf : Shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 6.dp, bottomStart = 22.dp),
    val bubblePeer : Shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 22.dp, bottomStart = 6.dp),

    /** Symmetrical bubble — grouped middle / reply preview. */
    val bubbleFlat : Shape = RoundedCornerShape(22.dp),

    /** Input composer / search field — pill-shaped. */
    val pill       : Shape = CircleShape,

    /** Bottom sheet — top corners only, 24-dp radius. */
    val sheet      : Shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomEnd = 0.dp, bottomStart = 0.dp),

    /** Settings row card grouping, large tap target. */
    val card       : Shape = RoundedCornerShape(18.dp),

    /** Colored squircle for icon tiles in Settings (`IcoTile` equivalent). */
    val iconTile   : Shape = RoundedCornerShape(9.dp),

    /** Koto logo tile — iOS rounded-square look. */
    val logoTile   : Shape = RoundedCornerShape(24.dp),
)

val DefaultKotoShapes = KotoShapes()
