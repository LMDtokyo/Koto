package run.koto.desktop.ui.screens.settings

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import run.koto.desktop.domain.model.LinkedDevice
import run.koto.desktop.ui.icons.KotoIcons
import run.koto.desktop.ui.theme.KotoTheme

/**
 * Linked-devices sub-screen. Mirrors what Telegram's "Active Sessions" and
 * Discord's "Devices" pages show: this-device card + remote sessions list,
 * with per-row "завершить" and a bottom red "завершить все другие" sweep.
 */
@Composable
fun DevicesScreen(viewModel: DevicesViewModel) {
    val colors    = KotoTheme.colors
    val status   by viewModel.status.collectAsState()
    val revoking by viewModel.revoking.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }
    DisposableEffect(Unit) { onDispose { viewModel.close() } }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text  = "Список устройств, на которых вы вошли. Завершите сессии, которыми не пользуетесь.",
            style = KotoTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(12.dp))

        when (val s = status) {
            DevicesViewModel.Status.Loading      -> LoadingBlock()
            is DevicesViewModel.Status.Failed    -> FailedBlock(s.message, onRetry = viewModel::load)
            is DevicesViewModel.Status.Loaded    -> {
                val current = s.devices.firstOrNull { it.id == s.currentId }
                val others  = s.devices.filter { it.id != s.currentId }
                // Guard: we cannot tell which row is "this device" yet (legacy
                // session bootstrapped from disk before SessionCoordinator's
                // heal-refresh completes). Telegram-style: do not let the user
                // revoke any row until current is resolved — otherwise they
                // can revoke themselves.
                val knowCurrent = s.currentId != null

                if (current != null) {
                    Text(
                        text       = "ЭТО УСТРОЙСТВО",
                        style      = KotoTheme.typography.labelMedium,
                        color      = colors.textSecondary,
                        fontWeight = FontWeight.SemiBold,
                        modifier   = Modifier.padding(start = 4.dp, bottom = 6.dp),
                    )
                    DeviceCard(device = current, isCurrent = true, busy = false, onRevoke = null)
                    Spacer(Modifier.height(20.dp))
                } else if (!knowCurrent) {
                    UnresolvedCurrentBanner()
                    Spacer(Modifier.height(20.dp))
                }

                if (others.isNotEmpty()) {
                    Text(
                        text       = if (current != null) "ДРУГИЕ СЕССИИ" else "АКТИВНЫЕ СЕССИИ",
                        style      = KotoTheme.typography.labelMedium,
                        color      = colors.textSecondary,
                        fontWeight = FontWeight.SemiBold,
                        modifier   = Modifier.padding(start = 4.dp, bottom = 6.dp),
                    )
                    others.forEach { dev ->
                        DeviceCard(
                            device    = dev,
                            isCurrent = false,
                            busy      = dev.id in revoking,
                            onRevoke  = if (knowCurrent) ({ viewModel.revoke(dev.id) }) else null,
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    if (knowCurrent) {
                        Spacer(Modifier.height(12.dp))
                        RevokeAllOthersButton(
                            busy    = others.any { it.id in revoking },
                            onClick = viewModel::revokeAllOthers,
                        )
                    }
                } else if (current != null) {
                    Text(
                        text     = "Других активных сессий нет.",
                        style    = KotoTheme.typography.bodyMedium,
                        color    = colors.textSecondary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device    : LinkedDevice,
    isCurrent : Boolean,
    busy      : Boolean,
    onRevoke  : (() -> Unit)?,
) {
    val colors = KotoTheme.colors
    val tile   = platformTile(device.platform)

    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier         = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(tile.color),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = tile.glyph, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = device.deviceName.ifBlank { "Неизвестное устройство" },
                style      = KotoTheme.typography.titleSmall,
                color      = colors.text,
                fontWeight = FontWeight.SemiBold,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
            val sub = buildString {
                if (device.platform.isNotBlank()) append(device.platform)
                if (device.appVersion.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append("v").append(device.appVersion)
                }
                if (device.clientIp.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(device.clientIp)
                }
            }.ifBlank { "—" }
            Text(
                text     = sub,
                style    = KotoTheme.typography.bodySmall,
                color    = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                text     = if (isCurrent) "активно сейчас" else "был(а) " + relativeTime(device.lastSeenAt),
                style    = KotoTheme.typography.labelMedium,
                color    = if (isCurrent) colors.accent else colors.textTertiary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (onRevoke != null) {
            RevokeChip(busy = busy, onClick = onRevoke)
        }
    }
}

@Composable
private fun RevokeChip(busy: Boolean, onClick: () -> Unit) {
    val colors = KotoTheme.colors
    val text   = if (busy) "…" else "завершить"
    val color  = if (busy) colors.textTertiary else colors.error
    Text(
        text     = text,
        style    = KotoTheme.typography.labelMedium,
        color    = color,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                enabled           = !busy,
                interactionSource = remember { MutableInteractionSource() },
                indication        = ripple(bounded = true),
                onClick           = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun RevokeAllOthersButton(busy: Boolean, onClick: () -> Unit) {
    val colors = KotoTheme.colors
    val bg     = colors.error.copy(alpha = if (busy) 0.4f else 1f)
    Box(
        modifier         = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(
                enabled           = !busy,
                interactionSource = remember { MutableInteractionSource() },
                indication        = ripple(bounded = true),
                onClick           = onClick,
            )
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text       = if (busy) "завершаем…" else "Завершить все другие сеансы",
            style      = KotoTheme.typography.titleMedium,
            color      = Color.White,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun UnresolvedCurrentBanner() {
    val colors = KotoTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .padding(16.dp),
    ) {
        Text(
            text  = "Определяем это устройство… revoke временно недоступен — синхронизируем данные с сервером.",
            style = KotoTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun LoadingBlock() {
    val colors = KotoTheme.colors
    Box(
        modifier         = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(colors.surface).padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "загружаем устройства…", style = KotoTheme.typography.bodyMedium, color = colors.textSecondary)
    }
}

@Composable
private fun FailedBlock(message: String, onRetry: () -> Unit) {
    val colors = KotoTheme.colors
    Column(
        modifier            = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(colors.surface).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = message, style = KotoTheme.typography.bodyMedium, color = colors.error)
        Spacer(Modifier.height(8.dp))
        Text(
            text     = "повторить",
            style    = KotoTheme.typography.labelMedium,
            color    = colors.accent,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = ripple(bounded = true),
                    onClick           = onRetry,
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

private data class PlatformTile(val color: Color, val glyph: androidx.compose.ui.graphics.vector.ImageVector)

private fun platformTile(platform: String): PlatformTile = when {
    platform.equals("Windows", ignoreCase = true) -> PlatformTile(Color(0xFF3276FF), KotoIcons.User)
    platform.equals("macOS",   ignoreCase = true) -> PlatformTile(Color(0xFF7C5CFF), KotoIcons.User)
    platform.equals("Linux",   ignoreCase = true) -> PlatformTile(Color(0xFFF0B400), KotoIcons.User)
    platform.equals("iOS",     ignoreCase = true) -> PlatformTile(Color(0xFF00A676), KotoIcons.Phone)
    platform.equals("Android", ignoreCase = true) -> PlatformTile(Color(0xFF13B3A5), KotoIcons.Phone)
    else                                          -> PlatformTile(Color(0xFF7C5CFF), KotoIcons.User)
}

/** "только что" / "5 мин назад" / "3 ч назад" / "2 дн назад" / absolute. */
private fun relativeTime(epochMillis: Long): String {
    if (epochMillis <= 0) return "недавно"
    val deltaSec = (System.currentTimeMillis() - epochMillis) / 1000
    return when {
        deltaSec < 60       -> "только что"
        deltaSec < 3600     -> "${deltaSec / 60} мин назад"
        deltaSec < 86_400   -> "${deltaSec / 3600} ч назад"
        deltaSec < 604_800  -> "${deltaSec / 86_400} дн назад"
        else                -> "${deltaSec / 604_800} нед назад"
    }
}
