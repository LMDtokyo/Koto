package run.koto.desktop.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import run.koto.desktop.ui.icons.KotoIcons
import run.koto.desktop.ui.theme.KotoTheme

/**
 * 12-word BIP39 recovery phrase entry screen. Supports:
 *   - per-field text entry with lowercase filter
 *   - paste (any field) — autodetects whitespace-separated words and fills all
 *   - live validation against [KOTO_WORDS] with red-border on invalid words
 *   - Continue button shows progress count until all 12 are filled
 */
@Composable
fun LoginScreen(
    viewModel : AuthViewModel,
    onBack    : () -> Unit,
) {
    val colors = KotoTheme.colors
    val state by viewModel.state.collectAsState()

    val words = remember { mutableStateListOf<String>().apply { repeat(SEED_WORD_COUNT) { add("") } } }

    Box(modifier = Modifier.fillMaxSize()) {
        AuthBackdrop()

        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top bar ────────────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                BackChip(label = "Назад", onClick = onBack)
                Text(
                    text       = "Восстановление",
                    style      = KotoTheme.typography.titleMedium,
                    color      = colors.text,
                    fontWeight = FontWeight.SemiBold,
                    modifier   = Modifier.weight(1f),
                    textAlign  = TextAlign.Center,
                )
                Spacer(Modifier.size(100.dp))   // balances the visible BackChip
            }

            // ── Scrollable body ───────────────────────────────────────────
            Box(
                modifier         = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier            = Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    KotoLogoTile(size = 56.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text       = "Введите $SEED_WORD_COUNT слов\nвашей фразы",
                        style      = KotoTheme.typography.headlineMedium,
                        color      = colors.text,
                        textAlign  = TextAlign.Center,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text      = "Порядок важен. Можно вставить всю фразу сразу — она распарсится по пробелам.",
                        style     = KotoTheme.typography.bodySmall,
                        color     = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(18.dp))

                    // Two-column grid of inputs
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(colors.surface.copy(alpha = 0.72f))
                            .border(1.dp, colors.separator, RoundedCornerShape(18.dp))
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
                                        SeedInput(
                                            index     = idx,
                                            word      = words[idx],
                                            onChange  = { new -> words[idx] = new },
                                            onPaste   = { pasted ->
                                                val parts = pasted.trim().split(Regex("\\s+"))
                                                if (parts.size >= 6) {
                                                    parts.take(SEED_WORD_COUNT).forEachIndexed { i, p ->
                                                        words[i] = p.lowercase()
                                                    }
                                                    true
                                                } else false
                                            },
                                            modifier  = Modifier.weight(1f),
                                        )
                                    } else {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    val invalid = words.any { it.isNotEmpty() && it !in KOTO_WORDS }
                    if (invalid) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text     = "некоторые слова не из словаря Koto",
                            style    = KotoTheme.typography.bodySmall,
                            color    = colors.error,
                            modifier = Modifier.align(Alignment.Start),
                        )
                    }
                    Spacer(Modifier.height(36.dp))
                }
            }

            // ── Bottom action ─────────────────────────────────────────────
            val filled = words.count { it.length >= 2 }
            val complete = filled == words.size && words.all { it in KOTO_WORDS }
            val loading = state is AuthViewModel.State.Loading
            Box(
                modifier         = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PrimaryButton(
                        label   = when {
                            loading  -> "восстанавливаем…"
                            complete -> "восстановить"
                            else     -> "введено $filled/$SEED_WORD_COUNT"
                        },
                        enabled = complete && !loading,
                        onClick = { viewModel.restore(words.toList()) },
                    )
                    if (state is AuthViewModel.State.Error) {
                        Text(
                            text      = (state as AuthViewModel.State.Error).msg,
                            style     = KotoTheme.typography.bodySmall,
                            color     = colors.error,
                            textAlign = TextAlign.Center,
                            modifier  = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeedInput(
    index    : Int,
    word     : String,
    onChange : (String) -> Unit,
    onPaste  : (String) -> Boolean,
    modifier : Modifier = Modifier,
) {
    val colors = KotoTheme.colors
    val isValid = word.isEmpty() || word in KOTO_WORDS

    Row(
        modifier              = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.background.copy(alpha = 0.5f))
            .border(
                width = 1.dp,
                color = if (isValid) androidx.compose.ui.graphics.Color.Transparent else colors.error,
                shape = RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text      = (index + 1).toString().padStart(2, ' '),
            style     = KotoTheme.typography.monoSmall,
            color     = colors.textSecondary,
            textAlign = TextAlign.End,
            modifier  = Modifier.width(18.dp),
        )
        Box(modifier = Modifier.weight(1f)) {
            if (word.isEmpty()) {
                Text(text = "—", style = KotoTheme.typography.bodyMedium, color = colors.textTertiary)
            }
            BasicTextField(
                value         = word,
                onValueChange = { new ->
                    // Detect paste-like input (whitespace in a single-word field)
                    if (new.contains(Regex("\\s")) && onPaste(new)) return@BasicTextField
                    val sanitized = new.lowercase().filter { it.isLetter() }
                    onChange(sanitized)
                },
                singleLine    = true,
                textStyle     = LocalTextStyle.current.merge(
                    KotoTheme.typography.bodyMedium.copy(
                        color = if (isValid) colors.text else colors.error,
                    ),
                ),
                cursorBrush   = SolidColor(colors.accent),
                modifier      = Modifier.fillMaxWidth(),
            )
        }
    }
}
