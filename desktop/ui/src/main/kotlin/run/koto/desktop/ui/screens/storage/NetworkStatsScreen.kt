package run.koto.desktop.ui.screens.storage

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import run.koto.desktop.domain.model.NetworkStats
import run.koto.desktop.ui.icons.KotoIcons
import run.koto.desktop.ui.theme.KotoTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Network-usage screen — sent / received counters since the last reset, plus a
 * reset button. Counters are accumulated by the byte-counter Ktor plugin and
 * persisted to the local SQLite row; this screen just reads.
 */
@Composable
fun NetworkStatsScreen(viewModel: NetworkStatsViewModel) {
    val stats by viewModel.state.collectAsState()
    val colors = KotoTheme.colors

    DisposableEffect(Unit) { onDispose { viewModel.close() } }

    var confirm by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text     = "Сколько данных Koto передал и принял с момента последнего сброса. Считается всё — переписки, медиа, синхронизация.",
            style    = KotoTheme.typography.bodyMedium,
            color    = colors.textSecondary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(12.dp))

        SummaryCard(stats = stats)
        Spacer(Modifier.height(16.dp))

        StatsRow(
            icon  = KotoIcons.Send,
            tile  = colors.accent,
            label = "Отправлено",
            value = formatBytes(stats.sentBytes),
        )
        Spacer(Modifier.height(8.dp))
        StatsRow(
            icon  = KotoIcons.Archive,
            tile  = colors.success,
            label = "Получено",
            value = formatBytes(stats.receivedBytes),
        )

        Spacer(Modifier.height(20.dp))

        ResetCard(onClick = { confirm = true })

        Spacer(Modifier.height(12.dp))
    }

    if (confirm) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { confirm = false }) {
            Column(
                modifier            = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.surface)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = "Сбросить статистику?", style = KotoTheme.typography.titleLarge, color = colors.text, fontWeight = FontWeight.SemiBold)
                Text(text = "Счётчики обнулятся, отметка времени станет текущей.", style = KotoTheme.typography.bodyMedium, color = colors.textSecondary)
                Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlineButton(label = "Отмена",  onClick = { confirm = false }, modifier = Modifier.weight(1f))
                    DangerButton (label = "Сбросить", onClick = { viewModel.reset(); confirm = false }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(stats: NetworkStats) {
    val colors = KotoTheme.colors
    val total  = stats.sentBytes + stats.receivedBytes
    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text       = formatBytes(total),
                style      = KotoTheme.typography.headlineLarge,
                color      = colors.text,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text     = "всего",
                style    = KotoTheme.typography.bodyMedium,
                color    = colors.textSecondary,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Text(
            text  = "С ${formatSince(stats.sinceAt)}",
            style = KotoTheme.typography.bodySmall,
            color = colors.textTertiary,
        )
    }
}

@Composable
private fun StatsRow(
    icon  : androidx.compose.ui.graphics.vector.ImageVector,
    tile  : Color,
    label : String,
    value : String,
) {
    val colors = KotoTheme.colors
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier         = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tile),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Text(
            text       = label,
            style      = KotoTheme.typography.bodyLarge,
            color      = colors.text,
            fontWeight = FontWeight.Medium,
            modifier   = Modifier.weight(1f),
        )
        Text(
            text       = value,
            style      = KotoTheme.typography.bodyLarge,
            color      = colors.textSecondary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ResetCard(onClick: () -> Unit) {
    val colors = KotoTheme.colors
    Box(
        modifier         = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = ripple(bounded = true),
                onClick           = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text       = "Сбросить статистику",
            style      = KotoTheme.typography.bodyLarge,
            color      = colors.error,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun OutlineButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = KotoTheme.colors
    Box(
        modifier         = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = ripple(bounded = true),
                onClick           = onClick,
            )
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = KotoTheme.typography.titleMedium, color = colors.text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DangerButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = KotoTheme.colors
    Box(
        modifier         = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.error)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = ripple(bounded = true),
                onClick           = onClick,
            )
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = KotoTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

/** "26 апреля 13:47" / "никогда" if epoch=0. */
private fun formatSince(epoch: Long): String {
    if (epoch <= 0L) return "сброса не было"
    val fmt = SimpleDateFormat("d MMMM HH:mm", Locale("ru"))
    return fmt.format(Date(epoch))
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 Б"
    val units = arrayOf("Б", "КБ", "МБ", "ГБ", "ТБ")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024.0 && i < units.lastIndex) { v /= 1024.0; i++ }
    return if (i == 0) "${bytes} ${units[i]}" else String.format("%.1f %s", v, units[i])
}
