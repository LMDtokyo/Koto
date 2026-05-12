package run.koto.desktop.ui.screens.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import run.koto.desktop.ui.icons.KotoIcons
import run.koto.desktop.ui.theme.KotoTheme

/**
 * Telegram-style pinned-message bar at the top of the chat. Shows a one-line
 * preview of the (most recent) pinned message with an accent stripe. Tapping
 * the body scrolls to the pinned message; tapping the close icon unpins.
 */
@Composable
fun PinnedBar(
    pinned  : Message,
    onClick : () -> Unit,
    onUnpin : () -> Unit,
) {
    val colors = KotoTheme.colors
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = ripple(bounded = true),
                onClick           = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 32.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.accent),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = "Закреплено",
                style      = KotoTheme.typography.labelMedium,
                color      = colors.accent,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text     = pinned.text.ifBlank { "(пустое сообщение)" },
                style    = KotoTheme.typography.bodySmall,
                color    = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        Box(
            modifier         = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = ripple(bounded = false, radius = 18.dp),
                    onClick           = onUnpin,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = KotoIcons.Close,
                contentDescription = "Открепить",
                tint               = colors.textTertiary,
                modifier           = Modifier.size(16.dp),
            )
        }
    }
    Box(modifier = Modifier
        .fillMaxWidth()
        .height(0.5.dp)
        .background(KotoTheme.colors.separator),
    )
}
