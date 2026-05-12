package run.koto.desktop.ui.screens.bots

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import run.koto.desktop.ui.icons.KotoIcons
import run.koto.desktop.ui.theme.KotoTheme

/**
 * Bot catalog — hero "Featured" strip + per-category sections. Clicking a bot
 * opens a chat with it (same pipeline as a user conversation — the mock bot
 * reply handler lives in [run.koto.desktop.ui.screens.conversation.mockReply]).
 */
@Composable
fun BotsScreen(
    onBack        : () -> Unit,
    onOpenBot     : (String) -> Unit,
    onOpenBotForge: () -> Unit,
) {
    val colors = KotoTheme.colors
    var query by remember { mutableStateOf("") }

    val filter = query.trim().lowercase()
    val allBots = if (filter.isEmpty()) BotCatalog.all
                  else BotCatalog.all.filter {
                      it.name.lowercase().contains(filter) ||
                      it.tagline.lowercase().contains(filter) ||
                      it.category.lowercase().contains(filter)
                  }
    val featured = allBots.filter { it.featured }
    val byCategory = allBots.groupBy { it.category }.toSortedMap()

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier              = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
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
                    text       = "Боты",
                    style      = KotoTheme.typography.titleLarge,
                    color      = colors.text,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier.weight(1f),
                    textAlign  = TextAlign.Center,
                )
                Row(
                    modifier              = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication        = ripple(bounded = false, radius = 60.dp),
                            onClick           = onOpenBotForge,
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector        = KotoIcons.Plus,
                        contentDescription = null,
                        tint               = colors.accent,
                        modifier           = Modifier.size(18.dp),
                    )
                    Text(text = "Создать", style = KotoTheme.typography.titleMedium, color = colors.accent)
                }
            }

            // Search
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector        = KotoIcons.Search,
                    contentDescription = null,
                    tint               = colors.textSecondary,
                    modifier           = Modifier.size(17.dp),
                )
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(text = "Искать бота", style = KotoTheme.typography.bodyMedium, color = colors.textTertiary)
                    }
                    BasicTextField(
                        value         = query,
                        onValueChange = { query = it },
                        singleLine    = true,
                        textStyle     = LocalTextStyle.current.merge(
                            KotoTheme.typography.bodyMedium.copy(color = colors.text),
                        ),
                        cursorBrush   = SolidColor(colors.accent),
                        modifier      = Modifier.fillMaxWidth(),
                    )
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)) {
                // Featured strip
                if (featured.isNotEmpty()) {
                    item("featured-label") {
                        Text(
                            text       = "ИЗБРАННОЕ",
                            style      = KotoTheme.typography.labelMedium,
                            color      = colors.textSecondary,
                            fontWeight = FontWeight.SemiBold,
                            modifier   = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 8.dp),
                        )
                    }
                    item("featured-strip") {
                        Row(
                            modifier              = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            featured.forEach { bot ->
                                FeaturedBotCard(bot = bot, onOpen = { onOpenBot(bot.id) })
                            }
                        }
                    }
                    item("featured-spacer") { Spacer(Modifier.height(20.dp)) }
                }

                byCategory.forEach { (cat, bots) ->
                    item("cat-$cat-label") {
                        Text(
                            text       = cat.uppercase(),
                            style      = KotoTheme.typography.labelMedium,
                            color      = colors.textSecondary,
                            fontWeight = FontWeight.SemiBold,
                            modifier   = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 6.dp),
                        )
                    }
                    item("cat-$cat-card") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.surface),
                        ) {
                            bots.forEachIndexed { i, bot ->
                                BotRow(
                                    bot     = bot,
                                    onOpen  = { onOpenBot(bot.id) },
                                )
                                if (i < bots.lastIndex) {
                                    Box(modifier = Modifier
                                        .padding(start = 60.dp)
                                        .fillMaxWidth()
                                        .height(0.5.dp)
                                        .background(colors.separator),
                                    )
                                }
                            }
                        }
                    }
                    item("cat-$cat-spacer") { Spacer(Modifier.height(14.dp)) }
                }

                item("bottom-pad") { Spacer(Modifier.height(40.dp)) }
            }
        }
    }
}

@Composable
private fun FeaturedBotCard(bot: KotoBot, onOpen: () -> Unit) {
    val colors = KotoTheme.colors
    Column(
        modifier = Modifier
            .width(260.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(bot.color.copy(alpha = 0.9f), bot.color.copy(alpha = 0.55f))))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = ripple(bounded = true),
                onClick           = onOpen,
            )
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier          = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f)),
                contentAlignment  = Alignment.Center,
            ) {
                Text(text = bot.initials, style = KotoTheme.typography.titleLarge, color = Color.White)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = bot.name,
                    style      = KotoTheme.typography.titleMedium,
                    color      = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text  = bot.handle,
                    style = KotoTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
            if (bot.verified) {
                Icon(
                    imageVector        = KotoIcons.CheckCircle,
                    contentDescription = "verified",
                    tint               = Color.White,
                    modifier           = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text     = bot.tagline,
            style    = KotoTheme.typography.bodyMedium,
            color    = Color.White.copy(alpha = 0.92f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector        = KotoIcons.User,
                contentDescription = null,
                tint               = Color.White.copy(alpha = 0.75f),
                modifier           = Modifier.size(12.dp),
            )
            Text(
                text  = "${bot.users} пользователей",
                style = KotoTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun BotRow(bot: KotoBot, onOpen: () -> Unit) {
    val colors = KotoTheme.colors
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = ripple(bounded = true),
                onClick           = onOpen,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier          = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(bot.color.copy(alpha = 0.15f))
                .border(1.dp, bot.color.copy(alpha = 0.4f), CircleShape),
            contentAlignment  = Alignment.Center,
        ) {
            Text(text = bot.initials, style = KotoTheme.typography.titleMedium, color = bot.color)
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text       = bot.name,
                    style      = KotoTheme.typography.titleSmall,
                    color      = colors.text,
                    fontWeight = FontWeight.SemiBold,
                )
                if (bot.verified) {
                    Icon(
                        imageVector        = KotoIcons.CheckCircle,
                        contentDescription = null,
                        tint               = colors.accent,
                        modifier           = Modifier.size(14.dp),
                    )
                }
            }
            Text(
                text     = bot.tagline,
                style    = KotoTheme.typography.bodySmall,
                color    = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(
            imageVector        = KotoIcons.ChevronRight,
            contentDescription = null,
            tint               = colors.textTertiary,
            modifier           = Modifier.size(16.dp),
        )
    }
}
