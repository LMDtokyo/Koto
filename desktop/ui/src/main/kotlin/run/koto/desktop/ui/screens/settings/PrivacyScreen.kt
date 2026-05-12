package run.koto.desktop.ui.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import run.koto.desktop.domain.model.PrivacyPreset
import run.koto.desktop.ui.theme.KotoTheme

/** Russian label for a [PrivacyPreset] — single source so the Settings list
 *  detail row, the preset row inside [PrivacyScreen], and any future surface
 *  always show the same wording. */
fun PrivacyPreset.russianLabel(): String = when (this) {
    PrivacyPreset.Standard -> "Стандартный"
    PrivacyPreset.Contacts -> "Только контакты"
    PrivacyPreset.Paranoid -> "Невидимка"
}

/**
 * Privacy screen — Signal-style grouped settings: short row title, no
 * scary red banners, footnote-style explanation under each block. The
 * preset row sits at the bottom as a quick-set rather than at the top
 * because individual toggles are the primary UI; presets are shortcuts.
 */
@Composable
fun PrivacyScreen(viewModel: PrivacyViewModel) {
    val state by viewModel.state.collectAsState()

    DisposableEffect(Unit) { onDispose { viewModel.close() } }

    Column(modifier = Modifier.fillMaxWidth()) {
        // ── Visibility ────────────────────────────────────────────────
        Section(
            header = "Видимость",
            footer = "Кто может найти вас и видеть, когда вы в сети.",
        ) {
            ToggleRow(
                title    = "Найти меня по имени",
                value    = state.discoverable,
                onChange = viewModel::toggleDiscoverable,
            )
            Divider()
            ToggleRow(
                title    = "Показывать активность",
                value    = state.showOnlineStatus,
                onChange = viewModel::toggleOnlineStatus,
                isLast   = true,
            )
        }

        // ── Messaging ─────────────────────────────────────────────────
        Section(
            header = "Сообщения",
            footer = "Эти сигналы видят собеседники. Выключите, если хочется тишины — придёт компромисс: вы тоже не будете видеть «печатает» и «прочитано» от других.",
        ) {
            ToggleRow(
                title    = "Прочитано",
                value    = state.sendReadReceipts,
                onChange = viewModel::toggleReadReceipts,
            )
            Divider()
            ToggleRow(
                title    = "«Печатает…»",
                value    = state.sendTyping,
                onChange = viewModel::toggleTyping,
                isLast   = true,
            )
        }

        // ── Notifications ─────────────────────────────────────────────
        Section(
            header = "Уведомления",
            footer = "Без превью в шторке OS будет «Новое сообщение» — текст увидите только в самом приложении.",
        ) {
            ToggleRow(
                title    = "Превью сообщений",
                value    = state.notifPreview,
                onChange = viewModel::toggleNotifPreview,
                isLast   = true,
            )
        }

        // ── Presets ───────────────────────────────────────────────────
        Section(
            header = "Шаблоны",
            footer = "Быстрая установка всех переключателей сразу. После — отдельные тумблеры можно подкрутить.",
        ) {
            PresetRow(
                label    = PrivacyPreset.Standard.russianLabel(),
                hint     = "Включено по умолчанию",
                selected = state.privacyPreset == PrivacyPreset.Standard,
                onClick  = { viewModel.pickPreset(PrivacyPreset.Standard) },
            )
            Divider()
            PresetRow(
                label    = PrivacyPreset.Contacts.russianLabel(),
                hint     = "Скрыть себя из публичного поиска",
                selected = state.privacyPreset == PrivacyPreset.Contacts,
                onClick  = { viewModel.pickPreset(PrivacyPreset.Contacts) },
            )
            Divider()
            PresetRow(
                label    = PrivacyPreset.Paranoid.russianLabel(),
                hint     = "Никаких исходящих сигналов",
                selected = state.privacyPreset == PrivacyPreset.Paranoid,
                onClick  = { viewModel.pickPreset(PrivacyPreset.Paranoid) },
                isLast   = true,
            )
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Section(
    header  : String,
    footer  : String?,
    content : @Composable () -> Unit,
) {
    val colors = KotoTheme.colors
    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(
            text       = header,
            style      = KotoTheme.typography.labelMedium,
            color      = colors.textSecondary,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface),
        ) { content() }
        if (footer != null) {
            Text(
                text     = footer,
                style    = KotoTheme.typography.bodySmall,
                color    = colors.textSecondary,
                modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 8.dp),
            )
        }
    }
}

@Composable
private fun Divider() {
    val colors = KotoTheme.colors
    Box(modifier = Modifier
        .fillMaxWidth()
        .padding(start = 16.dp)
        .height(0.5.dp)
        .background(colors.separator),
    )
}

@Composable
private fun ToggleRow(
    title    : String,
    value    : Boolean,
    onChange : (Boolean) -> Unit,
    isLast   : Boolean = false,
) {
    val colors = KotoTheme.colors
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = ripple(bounded = true),
                onClick           = { onChange(!value) },
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text       = title,
            style      = KotoTheme.typography.bodyLarge,
            color      = colors.text,
            fontWeight = FontWeight.Normal,
            modifier   = Modifier.weight(1f),
        )
        IosToggle(value = value, onChange = onChange)
    }
}

@Composable
private fun PresetRow(
    label    : String,
    hint     : String,
    selected : Boolean,
    onClick  : () -> Unit,
    isLast   : Boolean = false,
) {
    val colors = KotoTheme.colors
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = ripple(bounded = true),
                onClick           = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = label,
                style      = KotoTheme.typography.bodyLarge,
                color      = colors.text,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text     = hint,
                style    = KotoTheme.typography.bodySmall,
                color    = colors.textSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        RadioMark(selected = selected)
    }
}

/**
 * Hollow-ring → filled-with-inner-dot. Clean two-state radio drawn from
 * concentric circles instead of pulling in a Material Switch/RadioButton.
 */
@Composable
private fun RadioMark(selected: Boolean) {
    val colors = KotoTheme.colors
    val ringColor by animateColorAsState(
        targetValue = if (selected) colors.accent else colors.textTertiary,
        animationSpec = tween(180),
        label       = "radio-ring",
    )
    Box(
        modifier         = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(ringColor),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .padding(1.6.dp)
                .size(16.8.dp)
                .clip(CircleShape)
                .background(if (selected) ringColor else colors.surface),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                )
            }
        }
    }
}

/**
 * iOS-style toggle. Track widens on activation, thumb shifts horizontally
 * with a tween that matches the rest of the app's KotoEasing.
 */
@Composable
private fun IosToggle(value: Boolean, onChange: (Boolean) -> Unit) {
    val colors  = KotoTheme.colors
    val trackBg by animateColorAsState(
        targetValue = if (value) colors.accent else colors.separator,
        animationSpec = tween(220),
        label       = "track-bg",
    )
    val thumbX by animateDpAsState(
        targetValue   = if (value) 18.dp else 0.dp,
        animationSpec = tween(220),
        label         = "thumb-x",
    )
    Box(
        modifier = Modifier
            .size(width = 46.dp, height = 28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(trackBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = { onChange(!value) },
            ),
    ) {
        Box(
            modifier = Modifier
                .padding(2.dp)
                .offset(x = thumbX)
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}
