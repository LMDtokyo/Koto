package run.koto.desktop.ui.screens.others

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import run.koto.desktop.ui.components.atoms.Avatar
import run.koto.desktop.ui.icons.KotoIcons
import run.koto.desktop.ui.screens.chatlist.MockData
import run.koto.desktop.ui.theme.KotoTheme

/**
 * Stories grid — Instagram-/Telegram-style ephemeral stories preview.
 * Top strip = "Your story" + friends' unseen. Body = tiles organized per-author.
 * Tapping a tile would open the story viewer (deferred to Phase 4).
 */
@Composable
fun StoriesScreen(onBack: () -> Unit) {
    val colors   = KotoTheme.colors
    val contacts = MockData.contacts

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier              = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Row(
                    modifier              = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication        = ripple(bounded = false, radius = 60.dp),
                            onClick           = onBack,
                        )
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        imageVector        = KotoIcons.Back,
                        contentDescription = null,
                        tint               = colors.accent,
                        modifier           = Modifier.size(22.dp),
                    )
                    Text(text = "Чаты", style = KotoTheme.typography.titleMedium, color = colors.accent)
                }
                Text(
                    text       = "Истории",
                    style      = KotoTheme.typography.titleLarge,
                    color      = colors.text,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier.weight(1f),
                    textAlign  = TextAlign.Center,
                )
                Spacer(Modifier.size(80.dp))
            }

            // "Your story"
            Row(
                modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    Avatar(initials = "Я", color = colors.accent, size = 56.dp)
                    Box(
                        modifier          = Modifier
                            .size(22.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(colors.accent),
                        contentAlignment  = Alignment.Center,
                    ) {
                        Icon(
                            imageVector        = KotoIcons.Plus,
                            contentDescription = null,
                            tint               = Color.White,
                            modifier           = Modifier.size(14.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = "Моя история",
                        style      = KotoTheme.typography.titleSmall,
                        color      = colors.text,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text  = "Добавить фото или видео",
                        style = KotoTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }

            Spacer(Modifier.size(12.dp))

            Text(
                text       = "ДРУЗЬЯ",
                style      = KotoTheme.typography.labelMedium,
                color      = colors.textSecondary,
                fontWeight = FontWeight.SemiBold,
                modifier   = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
            )

            LazyVerticalGrid(
                columns               = GridCells.Adaptive(minSize = 150.dp),
                modifier              = Modifier.fillMaxSize(),
                contentPadding        = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement   = Arrangement.spacedBy(10.dp),
            ) {
                items(contacts) { contact ->
                    StoryTile(
                        name     = contact.name,
                        initials = contact.initials,
                        color    = contact.color,
                        unseen   = true,
                        onClick  = { /* open story viewer — Phase 4 */ },
                    )
                }
            }
        }
    }
}

@Composable
private fun StoryTile(
    name     : String,
    initials : String,
    color    : Color,
    unseen   : Boolean,
    onClick  : () -> Unit,
) {
    val colors = KotoTheme.colors
    Box(
        modifier          = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(colors = listOf(color.copy(alpha = 0.85f), color.copy(alpha = 0.4f))))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = ripple(bounded = true),
                onClick           = onClick,
            ),
    ) {
        // Avatar in top-left ring
        Box(
            modifier          = Modifier
                .size(44.dp)
                .align(Alignment.TopStart)
                .padding(8.dp)
                .clip(CircleShape)
                .background(if (unseen) colors.accent else colors.separator),
            contentAlignment  = Alignment.Center,
        ) {
            Box(
                modifier          = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment  = Alignment.Center,
            ) {
                Text(text = initials, color = Color.White, style = KotoTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
        }

        // Name on bottom, white-on-gradient
        Text(
            text       = name,
            style      = KotoTheme.typography.titleSmall,
            color      = Color.White,
            fontWeight = FontWeight.SemiBold,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
            modifier   = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}
