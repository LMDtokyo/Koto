package run.koto.desktop.ui.screens.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ripple
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import run.koto.desktop.ui.components.atoms.AnimatedIconButton
import run.koto.desktop.ui.components.atoms.Avatar
import run.koto.desktop.ui.icons.KotoIcons
import run.koto.desktop.ui.theme.KotoTheme

/**
 * Top-of-conversation bar — avatar + name + status on the left, quick-action
 * icons (phone / video / more) on the right. Matches the mockup navbar but
 * adapted for desktop: no back button in the main pane (sidebar persists), and
 * chrome uses flat background rather than iOS translucency since the window
 * itself supplies the blur.
 */
@Composable
fun ConversationHeader(
    name           : String,
    initials       : String,
    avatarColor    : Color,
    statusLine     : String,
    isOnline       : Boolean,
    ephemeralOn    : Boolean,
    onOpenContact  : () -> Unit,
    onOpenCall     : () -> Unit,
    onOpenVideo    : () -> Unit,
    onOpenSearch   : () -> Unit = {},
    onOpenMore     : () -> Unit,
) {
    val colors = KotoTheme.colors

    Column(modifier = Modifier.fillMaxWidth().background(colors.background)) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(KotoTheme.shapes.sm)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication        = ripple(bounded = false, radius = 60.dp),
                        onClick           = onOpenContact,
                    )
                    .padding(vertical = 6.dp, horizontal = 2.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Avatar(
                    initials = initials,
                    color    = avatarColor,
                    size     = 40.dp,
                    online   = isOnline,
                    pulse    = isOnline,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text       = name,
                            style      = KotoTheme.typography.titleSmall,
                            color      = colors.text,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis,
                            modifier   = Modifier.weight(1f, fill = false),
                        )
                        if (ephemeralOn) {
                            Icon(
                                imageVector        = KotoIcons.Timer,
                                contentDescription = null,
                                tint               = colors.accent,
                                modifier           = Modifier.size(14.dp),
                            )
                        }
                    }
                    Text(
                        text     = statusLine,
                        style    = KotoTheme.typography.labelMedium,
                        color    = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            AnimatedIconButton(icon = KotoIcons.Search, onClick = onOpenSearch)
            AnimatedIconButton(icon = KotoIcons.Phone,  onClick = onOpenCall)
            AnimatedIconButton(icon = KotoIcons.Video,  onClick = onOpenVideo)
            AnimatedIconButton(icon = KotoIcons.Dot,    onClick = onOpenMore)
        }

        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.separator))
    }
}

