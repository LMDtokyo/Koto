package run.koto.desktop.ui.screens.storage

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import run.koto.desktop.domain.model.ConversationStorage
import run.koto.desktop.domain.model.StorageInfo
import run.koto.desktop.ui.components.atoms.Avatar
import run.koto.desktop.ui.components.atoms.avatarColorFor
import run.koto.desktop.ui.icons.KotoIcons
import run.koto.desktop.ui.theme.KotoTheme

/**
 * Storage sub-screen — Telegram-style usage breakdown:
 *
 *   - Headline total + segmented bar (DB / Cache).
 *   - Per-conversation list sorted by descending size.
 *   - "Очистить кэш" and "Очистить все переписки" buttons with confirm dialog.
 *
 * Uses our local SQLite size + cache dir size; per-chat numbers come from
 * SUM(LENGTH(ciphertext)) via a SQLDelight aggregation, so they reflect real
 * on-disk cost rather than plaintext bytes.
 */
@Composable
fun StorageScreen(viewModel: StorageViewModel) {
    val info by viewModel.info.collectAsState()
    val busy by viewModel.busy.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }
    DisposableEffect(Unit) { onDispose { viewModel.close() } }

    var confirm by remember { mutableStateOf<ConfirmKind?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text     = "Здесь видно, сколько места занимает Koto на этом компьютере. Кэш можно безопасно очистить — переписки останутся.",
            style    = KotoTheme.typography.bodyMedium,
            color    = KotoTheme.colors.textSecondary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(12.dp))

        if (info == null) {
            LoadingBlock(text = "считаем место…")
            return@Column
        }

        TotalCard(info = info!!)
        Spacer(Modifier.height(16.dp))

        ActionsRow(
            busy            = busy,
            onClearCache    = { confirm = ConfirmKind.Cache },
            onClearMessages = { confirm = ConfirmKind.AllMessages },
        )
        Spacer(Modifier.height(20.dp))

        val nonEmptyChats = info!!.perConversation.filter { it.messageCount > 0 }
        if (nonEmptyChats.isNotEmpty()) {
            GroupHeader("ЧАТЫ · ${nonEmptyChats.size}")
            ListCard {
                nonEmptyChats.forEachIndexed { i, c ->
                    ChatStorageRow(
                        c        = c,
                        busy     = busy is StorageViewModel.BusyKind.ClearingConv && (busy as StorageViewModel.BusyKind.ClearingConv).convId == c.conversationId,
                        onClear  = { confirm = ConfirmKind.SingleConv(c) },
                    )
                    if (i != nonEmptyChats.lastIndex) RowDivider()
                }
            }
            Spacer(Modifier.height(12.dp))
        } else {
            Box(
                modifier         = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(KotoTheme.colors.surface)
                    .padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text  = "Сообщений ещё нет — поэтому и чистить нечего.",
                    style = KotoTheme.typography.bodyMedium,
                    color = KotoTheme.colors.textSecondary,
                )
            }
        }
    }

    confirm?.let { kind ->
        ConfirmDialog(
            kind     = kind,
            onCancel = { confirm = null },
            onAccept = {
                when (kind) {
                    ConfirmKind.Cache             -> viewModel.clearMediaCache()
                    ConfirmKind.AllMessages       -> viewModel.clearAllMessages()
                    is ConfirmKind.SingleConv     -> viewModel.clearConversation(kind.conv.conversationId)
                }
                confirm = null
            },
        )
    }
}

@Composable
private fun TotalCard(info: StorageInfo) {
    val colors = KotoTheme.colors
    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text       = formatBytes(info.totalBytes),
                style      = KotoTheme.typography.headlineLarge,
                color      = colors.text,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text       = "всего",
                style      = KotoTheme.typography.bodyMedium,
                color      = colors.textSecondary,
                modifier   = Modifier.padding(bottom = 6.dp),
            )
        }
        SegmentedBar(dbBytes = info.dbBytes, cacheBytes = info.cacheBytes)
        Row(
            modifier              = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Legend(
                dotColor = colors.accent,
                label    = "База данных",
                value    = formatBytes(info.dbBytes),
            )
            Legend(
                dotColor = colors.success,
                label    = "Кэш",
                value    = formatBytes(info.cacheBytes),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text     = "${info.totalMessageCount} сообщ.",
                style    = KotoTheme.typography.labelMedium,
                color    = colors.textTertiary,
            )
        }
    }
}

@Composable
private fun SegmentedBar(dbBytes: Long, cacheBytes: Long) {
    val colors = KotoTheme.colors
    val total  = (dbBytes + cacheBytes).coerceAtLeast(1L)
    val dbFrac    by animateFloatAsState(targetValue = dbBytes.toFloat() / total, animationSpec = tween(420), label = "db-frac")
    val cacheFrac by animateFloatAsState(targetValue = cacheBytes.toFloat() / total, animationSpec = tween(420), label = "cache-frac")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(colors.separator),
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(10.dp)) {
            if (dbFrac > 0f) Box(
                modifier = Modifier
                    .weight(dbFrac.coerceAtLeast(0.0001f))
                    .height(10.dp)
                    .background(colors.accent),
            )
            if (cacheFrac > 0f) Box(
                modifier = Modifier
                    .weight(cacheFrac.coerceAtLeast(0.0001f))
                    .height(10.dp)
                    .background(colors.success),
            )
            // Trailing empty filler so the row always has 1.0 of weight even when one slice is 0.
            Box(modifier = Modifier.weight((1f - dbFrac - cacheFrac).coerceAtLeast(0f).coerceAtLeast(0.0001f)).height(10.dp))
        }
    }
}

@Composable
private fun Legend(dotColor: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier
            .size(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(dotColor),
        )
        Column {
            Text(text = label, style = KotoTheme.typography.labelMedium, color = KotoTheme.colors.textSecondary)
            Text(text = value, style = KotoTheme.typography.bodySmall, color = KotoTheme.colors.text, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ActionsRow(
    busy            : StorageViewModel.BusyKind?,
    onClearCache    : () -> Unit,
    onClearMessages : () -> Unit,
) {
    val cacheBusy    = busy is StorageViewModel.BusyKind.ClearingCache
    val messagesBusy = busy is StorageViewModel.BusyKind.ClearingMessages
    val anyBusy      = busy != null

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ActionButton(
            label    = if (cacheBusy) "очищаем…" else "Очистить кэш",
            primary  = false,
            enabled  = !anyBusy,
            onClick  = onClearCache,
            modifier = Modifier.weight(1f),
        )
        ActionButton(
            label    = if (messagesBusy) "удаляем…" else "Очистить переписки",
            primary  = true,
            enabled  = !anyBusy,
            onClick  = onClearMessages,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ActionButton(
    label    : String,
    primary  : Boolean,
    enabled  : Boolean,
    onClick  : () -> Unit,
    modifier : Modifier = Modifier,
) {
    val colors = KotoTheme.colors
    val bg     = when {
        !enabled && primary -> colors.error.copy(alpha = 0.5f)
        primary             -> colors.error
        !enabled            -> colors.surface
        else                -> colors.surface
    }
    val fg     = when {
        primary             -> Color.White
        else                -> colors.text
    }
    Box(
        modifier         = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(
                enabled           = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication        = ripple(bounded = true),
                onClick           = onClick,
            )
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = KotoTheme.typography.titleMedium, color = fg, fontWeight = FontWeight.SemiBold)
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
    Box(modifier = Modifier
        .fillMaxWidth()
        .padding(start = 70.dp)
        .height(0.5.dp)
        .background(KotoTheme.colors.separator),
    )
}

@Composable
private fun ChatStorageRow(c: ConversationStorage, busy: Boolean, onClear: () -> Unit) {
    val colors = KotoTheme.colors
    Row(
        modifier              = Modifier
            .fillMaxWidth()
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
            Text(
                text       = c.displayName,
                style      = KotoTheme.typography.titleSmall,
                color      = colors.text,
                fontWeight = FontWeight.SemiBold,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
            Text(
                text     = "${c.messageCount} сообщ. · ${formatBytes(c.bytes)}",
                style    = KotoTheme.typography.labelMedium,
                color    = colors.textTertiary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        TextButton(
            label    = if (busy) "…" else "очистить",
            color    = if (busy) colors.textTertiary else colors.error,
            enabled  = !busy,
            onClick  = onClear,
        )
    }
}

@Composable
private fun TextButton(label: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text     = label,
        style    = KotoTheme.typography.labelMedium,
        color    = color,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                enabled           = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication        = ripple(bounded = true),
                onClick           = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun LoadingBlock(text: String) {
    val colors = KotoTheme.colors
    Box(
        modifier         = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = KotoTheme.typography.bodyMedium, color = colors.textSecondary)
    }
}

private sealed interface ConfirmKind {
    data object Cache              : ConfirmKind
    data object AllMessages        : ConfirmKind
    data class  SingleConv(val conv: ConversationStorage) : ConfirmKind
}

@Composable
private fun ConfirmDialog(kind: ConfirmKind, onCancel: () -> Unit, onAccept: () -> Unit) {
    val colors = KotoTheme.colors
    val (title, body, ok) = when (kind) {
        ConfirmKind.Cache         -> Triple(
            "Очистить кэш?",
            "Удалятся загруженные изображения и временные файлы. Сообщения и контакты не пострадают.",
            "Очистить",
        )
        ConfirmKind.AllMessages   -> Triple(
            "Удалить все сообщения?",
            "С этого устройства удалятся все локальные сообщения и кэш. Это действие нельзя отменить.",
            "Удалить",
        )
        is ConfirmKind.SingleConv -> Triple(
            "Очистить ${kind.conv.displayName}?",
            "Все сообщения этого чата будут удалены с этого устройства. Сам чат останется в списке.",
            "Очистить",
        )
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onCancel) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surface)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = title, style = KotoTheme.typography.titleLarge, color = colors.text, fontWeight = FontWeight.SemiBold)
            Text(text = body,  style = KotoTheme.typography.bodyMedium, color = colors.textSecondary)
            Row(
                modifier              = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ActionButton(label = "Отмена",  primary = false, enabled = true, onClick = onCancel, modifier = Modifier.weight(1f))
                ActionButton(label = ok,        primary = true,  enabled = true, onClick = onAccept, modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun initialsOf(name: String): String =
    name.split(' ', limit = 3)
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifBlank { "?" }

/** Human-readable byte sizes (KiB / MiB / GiB) — Russian locale uses dot as separator. */
private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 Б"
    val units = arrayOf("Б", "КБ", "МБ", "ГБ", "ТБ")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024.0 && i < units.lastIndex) { v /= 1024.0; i++ }
    return if (i == 0) "${bytes} ${units[i]}" else String.format("%.1f %s", v, units[i])
}
