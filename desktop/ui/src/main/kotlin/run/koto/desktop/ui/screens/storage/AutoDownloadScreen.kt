package run.koto.desktop.ui.screens.storage

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
import run.koto.desktop.ui.theme.KotoTheme

/**
 * Auto-download settings — per-media-type toggle plus an inline size cap for
 * videos and files. Telegram offers a tri-tab (Mobile / Wi-Fi / Roaming) page,
 * but on desktop only one tab applies, so we collapse to a single list.
 */
@Composable
fun AutoDownloadScreen(viewModel: AutoDownloadViewModel) {
    val state by viewModel.state.collectAsState()
    val colors = KotoTheme.colors

    DisposableEffect(Unit) { onDispose { viewModel.close() } }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text     = "Когда сообщение приходит, медиа загружается автоматически — но только для типов, выбранных ниже. Большие файлы можно ограничить по размеру.",
            style    = KotoTheme.typography.bodyMedium,
            color    = colors.textSecondary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(12.dp))

        Section(header = "ТИПЫ МЕДИА") {
            ToggleRow(
                title    = "Фотографии",
                hint     = "Загружаются всегда — обычно меньше 1 МБ",
                value    = state.photos,
                onChange = viewModel::togglePhotos,
            )
            Divider()
            ToggleRow(
                title    = "Видео",
                hint     = if (state.videos) "Файлы до ${formatLimit(state.videoMaxMb)}" else "Только по тапу",
                value    = state.videos,
                onChange = viewModel::toggleVideos,
            )
            if (state.videos) {
                Divider()
                LimitRow(
                    label   = "Лимит видео",
                    valueMb = state.videoMaxMb,
                    options = VIDEO_LIMITS,
                    onPick  = viewModel::setVideoMaxMb,
                )
            }
            Divider()
            ToggleRow(
                title    = "Файлы и документы",
                hint     = if (state.files) "Файлы до ${formatLimit(state.fileMaxMb)}" else "Только по тапу",
                value    = state.files,
                onChange = viewModel::toggleFiles,
            )
            if (state.files) {
                Divider()
                LimitRow(
                    label   = "Лимит файлов",
                    valueMb = state.fileMaxMb,
                    options = FILE_LIMITS,
                    onPick  = viewModel::setFileMaxMb,
                )
            }
            Divider()
            ToggleRow(
                title    = "Голосовые сообщения",
                hint     = "Загружаются всегда — обычно меньше 200 КБ",
                value    = state.voice,
                onChange = viewModel::toggleVoice,
                isLast   = true,
            )
        }

        Spacer(Modifier.height(20.dp))

        ResetCard(onClick = viewModel::reset)

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Section(
    header  : String,
    content : @Composable () -> Unit,
) {
    val colors = KotoTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text       = header,
            style      = KotoTheme.typography.labelMedium,
            color      = colors.textSecondary,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface),
        ) { content() }
    }
}

@Composable
private fun Divider() {
    Box(modifier = Modifier
        .fillMaxWidth()
        .padding(start = 16.dp)
        .height(0.5.dp)
        .background(KotoTheme.colors.separator),
    )
}

@Composable
private fun ToggleRow(
    title    : String,
    hint     : String?,
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = KotoTheme.typography.bodyLarge, color = colors.text, fontWeight = FontWeight.Normal)
            if (hint != null) {
                Text(
                    text     = hint,
                    style    = KotoTheme.typography.bodySmall,
                    color    = colors.textSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        IosToggle(value = value, onChange = onChange)
    }
}

@Composable
private fun LimitRow(
    label   : String,
    valueMb : Int,
    options : List<Int>,
    onPick  : (Int) -> Unit,
) {
    val colors = KotoTheme.colors
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(text = label, style = KotoTheme.typography.bodyMedium, color = colors.textSecondary)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { mb ->
                val selected = mb == valueMb
                Box(
                    modifier         = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) colors.accent else colors.background)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication        = ripple(bounded = true),
                            onClick           = { onPick(mb) },
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text       = formatLimit(mb),
                        style      = KotoTheme.typography.labelMedium,
                        color      = if (selected) Color.White else colors.text,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
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
            text       = "Сбросить настройки автозагрузки",
            style      = KotoTheme.typography.bodyLarge,
            color      = colors.error,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

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

private val VIDEO_LIMITS = listOf(5, 10, 25, 50, 0)
private val FILE_LIMITS  = listOf(10, 50, 100, 500, 0)

/** "5 МБ" / "10 МБ" / "0" → "без лимита". */
private fun formatLimit(mb: Int): String =
    if (mb <= 0) "без лимита" else "$mb МБ"
