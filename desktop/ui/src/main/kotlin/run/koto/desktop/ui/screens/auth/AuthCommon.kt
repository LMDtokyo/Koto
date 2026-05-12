package run.koto.desktop.ui.screens.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import run.koto.desktop.ui.icons.KotoIcons
import run.koto.desktop.ui.theme.KotoTheme

/**
 * Telegram-style labeled back button. Renders as "← Label" in accent color
 * with a ripple-bounded pill tap target. Used on auth sub-screens so the
 * back affordance is clearly visible — a bare circular icon against a dark
 * backdrop is too easy to miss.
 */
@Composable
fun BackChip(
    label   : String,
    onClick : () -> Unit,
) {
    val colors = KotoTheme.colors
    Row(
        modifier              = Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = ripple(bounded = false, radius = 60.dp),
                onClick           = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector        = KotoIcons.Back,
            contentDescription = null,
            tint               = colors.accent,
            modifier           = Modifier.size(22.dp),
        )
        Text(
            text       = label,
            style      = KotoTheme.typography.titleMedium,
            color      = colors.accent,
            fontWeight = FontWeight.Medium,
        )
    }
}
