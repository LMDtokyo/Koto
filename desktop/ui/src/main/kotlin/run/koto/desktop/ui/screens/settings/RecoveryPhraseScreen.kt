package run.koto.desktop.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import run.koto.desktop.ui.icons.KotoIcons
import run.koto.desktop.ui.theme.KotoTheme

/**
 * "Recovery phrase" sub-screen. Telegram-style reveal-on-demand:
 *   1. Warning card + big red "Показать фразу" button (idle).
 *   2. Tap → 12-word grid + copy + "Скрыть".
 *
 * The screen never auto-reveals — the user has to actively confirm. This
 * mirrors how Trust Wallet, MetaMask and 1Password show seed phrases.
 */
@Composable
fun RecoveryPhraseScreen(viewModel: RecoveryPhraseViewModel) {
    val colors    = KotoTheme.colors
    val state    by viewModel.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    var copied    by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { viewModel.close() } }
    LaunchedEffect(copied) { if (copied) { delay(1500); copied = false } }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text  = "Эта фраза — единственный способ восстановить доступ к Koto ID. Запишите её и храните офлайн.",
            style = KotoTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        )

        Spacer(Modifier.height(12.dp))

        WarningBlock()

        Spacer(Modifier.height(16.dp))

        when (val s = state) {
            RecoveryPhraseViewModel.State.Hidden ->
                RevealButton(onClick = viewModel::reveal)

            RecoveryPhraseViewModel.State.Loading ->
                LoadingBlock()

            RecoveryPhraseViewModel.State.Unavailable ->
                UnavailableBlock()

            is RecoveryPhraseViewModel.State.Revealed -> {
                WordGrid(words = s.words)
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SecondaryButton(
                        label    = if (copied) "скопировано ✓" else "копировать",
                        accent   = copied,
                        onClick  = {
                            clipboard.setText(AnnotatedString(s.words.joinToString(" ")))
                            copied = true
                        },
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(
                        label    = "скрыть",
                        accent   = false,
                        onClick  = viewModel::hide,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun WarningBlock() {
    val colors = KotoTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.error.copy(alpha = 0.10f))
            .border(1.dp, colors.error.copy(alpha = 0.30f), RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                imageVector        = KotoIcons.Alert,
                contentDescription = null,
                tint               = colors.error,
                modifier           = Modifier.size(16.dp),
            )
            Text(
                text       = "ВАЖНО",
                style      = KotoTheme.typography.labelSmall,
                color      = colors.error,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(6.dp))
        BulletText("Никому не показывайте эту фразу — это полный доступ к вашему Koto ID.")
        BulletText("Koto-команда никогда её не запросит.")
        BulletText("Перепишите на бумагу. Скриншот хранится в облаке и его могут украсть.")
    }
}

@Composable
private fun BulletText(text: String) {
    val colors = KotoTheme.colors
    Row(
        modifier              = Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment     = Alignment.Top,
    ) {
        Text(text = "•", style = KotoTheme.typography.bodySmall, color = colors.error)
        Text(text = text, style = KotoTheme.typography.bodySmall, color = colors.text)
    }
}

@Composable
private fun RevealButton(onClick: () -> Unit) {
    val colors = KotoTheme.colors
    Box(
        modifier         = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.accent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = ripple(bounded = true),
                onClick           = onClick,
            )
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text       = "Показать фразу",
            style      = KotoTheme.typography.titleMedium,
            color      = Color.White,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SecondaryButton(label: String, accent: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = KotoTheme.colors
    val fg     = if (accent) colors.accent else colors.text
    Box(
        modifier         = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, colors.separator, RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = ripple(bounded = true),
                onClick           = onClick,
            )
            .padding(vertical = 12.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = KotoTheme.typography.titleSmall, color = fg, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun WordGrid(words: List<String>) {
    val colors = KotoTheme.colors
    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(1.dp, colors.separator, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val rows = (words.size + 1) / 2
        for (r in 0 until rows) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                listOf(r, r + rows).forEach { idx ->
                    if (idx < words.size) {
                        WordCell(index = idx + 1, word = words[idx], modifier = Modifier.weight(1f))
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun WordCell(index: Int, word: String, modifier: Modifier = Modifier) {
    val colors = KotoTheme.colors
    Row(
        modifier              = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.background)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text     = index.toString().padStart(2, ' '),
            style    = KotoTheme.typography.monoSmall,
            color    = colors.textSecondary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(20.dp),
        )
        Text(
            text       = word,
            style      = KotoTheme.typography.bodyMedium,
            color      = colors.text,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun LoadingBlock() {
    val colors = KotoTheme.colors
    Box(
        modifier         = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "расшифровываем…", style = KotoTheme.typography.bodyMedium, color = colors.textSecondary)
    }
}

@Composable
private fun UnavailableBlock() {
    val colors = KotoTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .padding(16.dp),
    ) {
        Text(
            text       = "Фраза недоступна",
            style      = KotoTheme.typography.titleSmall,
            color      = colors.text,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text  = "Локальные ключи могли быть очищены, либо аккаунт восстановлен из облака без локальной фразы. Создайте новый Koto ID или восстановите по фразе из бумажной копии.",
            style = KotoTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
}
