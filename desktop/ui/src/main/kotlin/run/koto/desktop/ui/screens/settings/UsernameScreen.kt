package run.koto.desktop.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import run.koto.desktop.ui.theme.KotoColors
import run.koto.desktop.ui.theme.KotoTheme

/**
 * Username sub-screen. Mirrors Telegram's `@username` form:
 *   - prefix "@" inside the input visually (the user types only the body)
 *   - lowercase auto-filter on input, [a–z 0–9 _] only
 *   - debounced availability probe (300 ms) running through [UsernameViewModel]
 *   - colour-coded line under the input: gray = idle, accent = checking,
 *     success = available, error = taken / invalid
 *   - Save button only enabled when the candidate is available AND differs
 *     from the currently saved username
 */
@Composable
fun UsernameScreen(viewModel: UsernameViewModel) {
    val colors = KotoTheme.colors
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }
    DisposableEffect(Unit) { onDispose { viewModel.close() } }

    val (lineColor, lineText) = lineFor(state, colors)
    val canSave = state.check is UsernameViewModel.CheckState.Available &&
                  state.save  !is UsernameViewModel.SaveState.Saving

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text  = "Установите имя пользователя — другие смогут найти вас по @имени без обмена Koto ID.",
            style = KotoTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface)
                .border(1.dp, lineColor.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text       = "@",
                style      = KotoTheme.typography.titleMedium,
                color      = colors.textSecondary,
                fontWeight = FontWeight.SemiBold,
            )
            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                if (state.candidate.isEmpty()) {
                    Text(
                        text  = "username",
                        style = KotoTheme.typography.titleMedium,
                        color = colors.textTertiary,
                    )
                }
                BasicTextField(
                    value         = state.candidate,
                    onValueChange = viewModel::onCandidateChange,
                    singleLine    = true,
                    textStyle     = LocalTextStyle.current.merge(
                        KotoTheme.typography.titleMedium.copy(color = colors.text)
                    ),
                    cursorBrush   = SolidColor(colors.accent),
                    modifier      = Modifier.fillMaxWidth(),
                )
            }
            // Right-side glyph mirrors the underline state at a glance.
            StatusGlyph(state, colors)
        }

        AnimatedVisibility(
            visible = lineText != null,
            enter   = fadeIn(tween(180)),
            exit    = fadeOut(tween(140)),
        ) {
            Text(
                text     = lineText.orEmpty(),
                style    = KotoTheme.typography.bodySmall,
                color    = lineColor,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp),
            )
        }

        Spacer(Modifier.height(20.dp))

        SaveButton(
            enabled = canSave,
            saving  = state.save is UsernameViewModel.SaveState.Saving,
            saved   = state.save is UsernameViewModel.SaveState.Saved,
            onClick = viewModel::save,
        )

        if (state.save is UsernameViewModel.SaveState.Failed) {
            Text(
                text     = (state.save as UsernameViewModel.SaveState.Failed).message,
                style    = KotoTheme.typography.bodySmall,
                color    = colors.error,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun StatusGlyph(state: UsernameViewModel.UiState, colors: KotoColors) {
    val (text, color) = when (state.check) {
        UsernameViewModel.CheckState.Available     -> "✓"  to colors.accent
        UsernameViewModel.CheckState.Taken         -> "✕"  to colors.error
        is UsernameViewModel.CheckState.Invalid    -> "!"  to colors.error
        UsernameViewModel.CheckState.Checking      -> "…"  to colors.textSecondary
        UsernameViewModel.CheckState.Idle          -> null to colors.textTertiary
    }
    if (text != null) {
        Text(
            text       = text,
            style      = KotoTheme.typography.titleMedium,
            color      = color,
            fontWeight = FontWeight.Bold,
            modifier   = Modifier.padding(start = 8.dp),
        )
    }
}

private fun lineFor(state: UsernameViewModel.UiState, colors: KotoColors): Pair<Color, String?> = when {
    state.candidate.isEmpty() && state.savedUsername == null
        -> colors.textTertiary to "минимум 5 символов, можно a–z 0–9 _"
    state.candidate == state.savedUsername
        -> colors.textSecondary to "это ваше текущее имя"
    state.check is UsernameViewModel.CheckState.Checking
        -> colors.textSecondary to "проверяем…"
    state.check is UsernameViewModel.CheckState.Available
        -> colors.accent to "имя свободно"
    state.check is UsernameViewModel.CheckState.Taken
        -> colors.error to "имя занято"
    state.check is UsernameViewModel.CheckState.Invalid
        -> colors.error to (state.check as UsernameViewModel.CheckState.Invalid).reason
    else
        -> colors.textTertiary to null
}

@Composable
private fun SaveButton(enabled: Boolean, saving: Boolean, saved: Boolean, onClick: () -> Unit) {
    val colors  = KotoTheme.colors
    val bg      = when {
        saved   -> colors.accent.copy(alpha = 0.35f)
        enabled -> colors.accent
        else    -> colors.surface
    }
    val fg      = if (enabled || saved) Color.White else colors.textTertiary
    val baseModifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(bg)
    val rowModifier = if (enabled) {
        baseModifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication        = ripple(bounded = true),
            onClick           = onClick,
        )
    } else baseModifier

    Box(
        modifier         = rowModifier.padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text       = when {
                saving -> "сохраняем…"
                saved  -> "сохранено ✓"
                else   -> "сохранить"
            },
            style      = KotoTheme.typography.titleMedium,
            color      = fg,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
