package run.koto.desktop.ui.screens.others

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import run.koto.desktop.ui.icons.KotoIcons
import run.koto.desktop.ui.theme.KotoTheme

/**
 * "New Group" form. Accepts:
 *   - group name (free text)
 *   - one or more participants — typed as Koto ID (64 hex) or @username and
 *     committed to the chip list with the "+" button
 *
 * On confirm we POST /v1/conversations with type=2 and the resolved member
 * ids. The backend returns the conversation_id; we navigate the host stack
 * straight to the new chat.
 */
@Composable
fun NewGroupScreen(
    onBack       : () -> Unit,
    onOpenChat   : (String) -> Unit,
) {
    val colors = KotoTheme.colors
    val vm: NewGroupViewModel = koinInject()
    val state by vm.state.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top bar ────────────────────────────────────────────────────
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
                    Icon(KotoIcons.Back, null, tint = colors.accent, modifier = Modifier.size(22.dp))
                    Text(text = "Назад", style = KotoTheme.typography.titleMedium, color = colors.accent)
                }
                Text(
                    text       = "Новая группа",
                    style      = KotoTheme.typography.titleLarge,
                    color      = colors.text,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier.weight(1f),
                    textAlign  = TextAlign.Center,
                )
                Spacer(Modifier.size(80.dp))
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
            ) {
                // Group name
                FieldLabel("Название группы")
                NamedField(
                    value         = state.name,
                    onValueChange = vm::onNameChange,
                    placeholder   = "например, Команда Koto",
                )

                Spacer(Modifier.height(20.dp))

                // Member input
                FieldLabel("Добавить участников")
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        NamedField(
                            value         = state.candidate,
                            onValueChange = vm::onCandidateChange,
                            placeholder   = "@username или Koto ID",
                        )
                    }
                    val canAdd = state.candidateResolution is NewGroupViewModel.CandidateRes.Resolved
                    Box(
                        modifier         = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (canAdd) colors.accent else colors.surface)
                            .clickable(
                                enabled           = canAdd,
                                interactionSource = remember { MutableInteractionSource() },
                                indication        = ripple(bounded = true),
                                onClick           = vm::commitCandidate,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector        = KotoIcons.Plus,
                            contentDescription = "Добавить",
                            tint               = if (canAdd) Color.White else colors.textTertiary,
                            modifier           = Modifier.size(20.dp),
                        )
                    }
                }

                // Resolution hint under the field
                val hint = when (val r = state.candidateResolution) {
                    NewGroupViewModel.CandidateRes.Idle      -> null
                    NewGroupViewModel.CandidateRes.Resolving -> "ищем…"
                    is NewGroupViewModel.CandidateRes.Resolved -> "найдено: ${r.label}"
                    is NewGroupViewModel.CandidateRes.Invalid  -> r.reason
                    NewGroupViewModel.CandidateRes.NotFound    -> "не нашли такой профиль"
                }
                if (hint != null) {
                    Text(
                        text     = hint,
                        style    = KotoTheme.typography.labelMedium,
                        color    = if (state.candidateResolution is NewGroupViewModel.CandidateRes.Resolved) colors.accent else colors.textSecondary,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Member chip list
                if (state.members.isNotEmpty()) {
                    state.members.forEach { m ->
                        MemberChip(label = m.label, sub = m.accountId, onRemove = { vm.removeMember(m.accountId) })
                        Spacer(Modifier.height(8.dp))
                    }
                }

                Spacer(Modifier.weight(1f))

                if (state.createError != null) {
                    Text(
                        text     = state.createError.orEmpty(),
                        style    = KotoTheme.typography.bodySmall,
                        color    = colors.error,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }

                val canCreate = state.members.isNotEmpty() && !state.creating
                Box(
                    modifier         = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (canCreate) colors.accent else colors.surface)
                        .clickable(
                            enabled           = canCreate,
                            interactionSource = remember { MutableInteractionSource() },
                            indication        = ripple(bounded = true),
                            onClick           = { vm.create(onOpenChat) },
                        )
                        .padding(vertical = 14.dp, horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text       = when {
                            state.creating          -> "создаём…"
                            state.members.isEmpty() -> "добавьте участников"
                            else                    -> "создать группу"
                        },
                        style      = KotoTheme.typography.titleMedium,
                        color      = if (canCreate) Color.White else colors.textTertiary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    val colors = KotoTheme.colors
    Text(
        text       = text.uppercase(),
        style      = KotoTheme.typography.labelSmall,
        color      = colors.textSecondary,
        fontWeight = FontWeight.SemiBold,
        modifier   = Modifier.padding(start = 4.dp, bottom = 6.dp),
    )
}

@Composable
private fun NamedField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    val colors = KotoTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, colors.separator, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty()) {
            Text(
                text  = placeholder,
                style = KotoTheme.typography.bodyMedium,
                color = colors.textTertiary,
            )
        }
        BasicTextField(
            value         = value,
            onValueChange = onValueChange,
            singleLine    = true,
            textStyle     = LocalTextStyle.current.merge(
                KotoTheme.typography.bodyMedium.copy(color = colors.text),
            ),
            cursorBrush   = SolidColor(colors.accent),
            modifier      = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MemberChip(label: String, sub: String, onRemove: () -> Unit) {
    val colors = KotoTheme.colors
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = label,
                style      = KotoTheme.typography.titleSmall,
                color      = colors.text,
                fontWeight = FontWeight.SemiBold,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
            Text(
                text     = "${sub.take(10)}…${sub.takeLast(6)}",
                style    = KotoTheme.typography.monoSmall,
                color    = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier         = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = ripple(bounded = true),
                    onClick           = onRemove,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = KotoIcons.Close,
                contentDescription = "Удалить",
                tint               = colors.textSecondary,
                modifier           = Modifier.size(14.dp),
            )
        }
    }
}
