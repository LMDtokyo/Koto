package run.koto.desktop.ui.screens.chatlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import run.koto.desktop.ui.icons.KotoIcons
import run.koto.desktop.ui.theme.KotoTheme

/**
 * Archive screen — shows conversations the user has swiped into the archive.
 * Single swipe action (unarchive) restores a row to the main list. Backed by
 * the same [ChatListViewModel] so the underlying SQLite-backed flow drives
 * both screens consistently.
 */
@Composable
fun ArchiveScreen(
    state      : ChatListState,
    onBack     : () -> Unit,
    onOpenChat : (String) -> Unit,
) {
    val colors     = KotoTheme.colors
    val typography = KotoTheme.typography

    val vm: ChatListViewModel = koinInject()
    val archivedChats by vm.archived.collectAsState()

    val openRowMap: SnapshotStateMap<String, SwipeRowAnchor> = remember { mutableStateMapOf() }

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        // ── Header — iOS-style "< Чаты" + centered title ─────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.background)
                .padding(top = 18.dp, start = 8.dp, end = 8.dp, bottom = 12.dp),
        ) {
            Row(
                modifier              = Modifier.clickable(onClick = onBack).padding(8.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Icon(imageVector = KotoIcons.Back, contentDescription = "Назад", tint = colors.accent, modifier = Modifier.size(26.dp))
                Text(text = "Чаты", style = typography.titleLarge, color = colors.accent)
            }
            Text(
                text     = "Архив",
                style    = typography.titleLarge,
                color    = colors.text,
                modifier = Modifier.align(Alignment.Center).padding(end = 50.dp),
            )
        }
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(colors.separator))

        if (archivedChats.isEmpty()) {
            Column(
                modifier             = Modifier.fillMaxSize().padding(top = 80.dp, start = 32.dp, end = 32.dp),
                horizontalAlignment  = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier         = Modifier.size(72.dp).clip(CircleShape).background(colors.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(imageVector = KotoIcons.Archive, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text("Архив пуст", style = typography.titleLarge, color = colors.text)
                Spacer(Modifier.height(6.dp))
                Text(
                    text  = "Смахните чат влево на списке, чтобы отправить его сюда.",
                    style = typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Text(
                        text     = "Чаты в архиве не показываются в списке и не присылают звуки уведомлений. " +
                                   "Новое сообщение вернёт чат обратно.",
                        style    = typography.bodySmall,
                        color    = colors.textSecondary,
                        modifier = Modifier.padding(top = 14.dp, start = 20.dp, end = 20.dp, bottom = 6.dp),
                    )
                }

                items(archivedChats, key = { it.id }) { chat ->
                    val leftActions = listOf(
                        SwipeAction(
                            id           = "unarchive",
                            background   = colors.success,
                            foreground   = Color.White,
                            icon         = KotoIcons.Unarchive,
                            label        = "Из архива",
                            commitOnFull = true,
                            onAction     = { vm.setArchived(chat.id, false) },
                        ),
                    )

                    SwipeRow(
                        id           = "arc-${chat.id}",
                        leftActions  = leftActions,
                        rightActions = emptyList(),
                        openRowId    = openRowMap,
                        onOpen       = { onOpenChat(chat.id) },
                        rowBackground = colors.background,
                        rowHeight    = 72.dp,
                    ) {
                        ChatRowPublic(chat = chat)
                    }
                }

                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }
}
