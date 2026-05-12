package run.koto.desktop.ui.screens.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.koin.compose.koinInject
import run.koto.desktop.domain.repository.ConversationRepository
import run.koto.desktop.ui.components.atoms.Avatar
import run.koto.desktop.ui.components.atoms.avatarColorFor
import run.koto.desktop.ui.theme.KotoTheme

/**
 * Modal picker — pick a destination conversation for a forward. Lists every
 * conversation except the current one. Tap a row → callback fires + dialog
 * dismisses; tap outside → cancel. Single-pick to start; multi-select can
 * follow once the UX warrants it.
 */
@Composable
fun ForwardPicker(
    excludeConvId : String,
    onPick        : (convId: String) -> Unit,
    onDismiss     : () -> Unit,
) {
    val colors = KotoTheme.colors
    val convRepo: ConversationRepository = koinInject()
    val all by convRepo.observeAll().collectAsState(initial = emptyList())
    val items = remember(all, excludeConvId) { all.filterNot { it.id == excludeConvId } }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(min = 360.dp, max = 480.dp)
                .heightIn(min = 200.dp, max = 600.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surface),
        ) {
            Box(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Text(
                    text       = "Переслать в…",
                    style      = KotoTheme.typography.titleMedium,
                    color      = colors.text,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Box(modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(colors.separator),
            )
            if (items.isEmpty()) {
                Box(
                    modifier         = Modifier.fillMaxSize().padding(28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text  = "Других чатов нет",
                        style = KotoTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(vertical = 4.dp)) {
                    items(items, key = { it.id }) { conv ->
                        Row(
                            modifier              = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication        = ripple(bounded = true),
                                    onClick           = { onPick(conv.id) },
                                )
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Avatar(
                                initials = initialsForRow(conv.displayName),
                                color    = avatarColorFor(conv.peerAccountId.ifBlank { conv.id }),
                                size     = 36.dp,
                            )
                            Text(
                                text       = conv.displayName.ifBlank { "Без имени" },
                                style      = KotoTheme.typography.bodyLarge,
                                color      = colors.text,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun initialsForRow(name: String): String =
    name.split(' ', limit = 3)
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifBlank { "?" }
