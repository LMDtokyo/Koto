package run.koto.desktop.ui.screens.safety

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import run.koto.desktop.domain.model.Conversation
import run.koto.desktop.ui.components.atoms.Avatar
import run.koto.desktop.ui.components.atoms.avatarColorFor
import run.koto.desktop.ui.icons.KotoIcons
import run.koto.desktop.ui.theme.KotoTheme

/**
 * Settings → Проверка безопасности — Signal-style verification list.
 * Conversations split into two groups: "Не проверено" / "Проверено".
 * A row tap opens the per-contact detail screen via [onOpen].
 */
@Composable
fun SafetyListScreen(
    viewModel: SafetyListViewModel,
    onOpen   : (convId: String) -> Unit,
) {
    val colors = KotoTheme.colors
    val all by viewModel.conversations.collectAsState()

    DisposableEffect(Unit) { onDispose { viewModel.close() } }

    val unverified = all.filterNot { it.isVerified }
    val verified   = all.filter   { it.isVerified }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text  = "Сравните номера безопасности с собеседником, чтобы убедиться, что между вами никто не вклинился. Один раз — навсегда.",
            style = KotoTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(12.dp))

        if (all.isEmpty()) {
            EmptyBlock()
            return@Column
        }

        if (unverified.isNotEmpty()) {
            GroupHeader("НЕ ПРОВЕРЕНО · ${unverified.size}")
            ListCard {
                unverified.forEachIndexed { i, c ->
                    SafetyRow(c, onClick = { onOpen(c.id) })
                    if (i != unverified.lastIndex) RowDivider()
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        if (verified.isNotEmpty()) {
            GroupHeader("ПРОВЕРЕНО · ${verified.size}")
            ListCard {
                verified.forEachIndexed { i, c ->
                    SafetyRow(c, onClick = { onOpen(c.id) })
                    if (i != verified.lastIndex) RowDivider()
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun GroupHeader(text: String) {
    Text(
        text       = text,
        style      = KotoTheme.typography.labelMedium,
        color      = KotoTheme.colors.textSecondary,
        fontWeight = FontWeight.SemiBold,
        modifier   = Modifier.padding(start = 4.dp, bottom = 6.dp),
    )
}

@Composable
private fun ListCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(KotoTheme.colors.surface),
    ) { content() }
}

@Composable
private fun RowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 70.dp)
            .height(0.5.dp)
            .background(KotoTheme.colors.separator),
    )
}

@Composable
private fun SafetyRow(c: Conversation, onClick: () -> Unit) {
    val colors = KotoTheme.colors
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = ripple(bounded = true),
                onClick           = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(
            initials = initialsOf(c.displayName),
            color    = avatarColorFor(c.peerAccountId),
            size     = 42.dp,
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text       = c.displayName,
                    style      = KotoTheme.typography.titleSmall,
                    color      = colors.text,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    modifier   = Modifier.weight(1f, fill = false),
                )
                if (c.isVerified) VerifiedBadge()
            }
            Text(
                text     = abbreviateAccountId(c.peerAccountId),
                style    = KotoTheme.typography.labelMedium,
                color    = colors.textTertiary,
                maxLines = 1,
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

@Composable
private fun VerifiedBadge() {
    val colors = KotoTheme.colors
    Box(
        modifier         = Modifier.size(16.dp).clip(CircleShape).background(colors.success),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector        = KotoIcons.Check,
            contentDescription = null,
            tint               = Color.White,
            modifier           = Modifier.size(10.dp),
        )
    }
}

@Composable
private fun EmptyBlock() {
    val colors = KotoTheme.colors
    Box(
        modifier         = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text  = "У вас пока нет диалогов. Начните чат — и его можно будет проверить здесь.",
            style = KotoTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
}

private fun initialsOf(name: String): String =
    name.split(' ', limit = 3)
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifBlank { "?" }

private fun abbreviateAccountId(id: String): String =
    if (id.length > 14) id.take(8) + "…" + id.takeLast(4) else id
