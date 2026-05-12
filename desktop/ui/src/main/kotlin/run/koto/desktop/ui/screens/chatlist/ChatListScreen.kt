package run.koto.desktop.ui.screens.chatlist

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import org.koin.compose.koinInject
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import run.koto.desktop.ui.components.atoms.AnimatedIconButton
import run.koto.desktop.ui.components.atoms.Avatar
import run.koto.desktop.ui.components.atoms.EmptyState
import run.koto.desktop.ui.icons.KotoIcons
import run.koto.desktop.ui.icons.svgIcon
import run.koto.desktop.ui.theme.KotoTheme

/**
 * Chat list screen — faithful Compose port of `ChatList.jsx`.
 *
 * Layout (top-to-bottom, matching mockup):
 *   1. Header strip: profile-avatar button (left) + bots/camera/compose buttons (right)
 *   2. Large title "Koto" + inline "X новых" unread counter
 *   3. Rounded search field
 *   4. Horizontal tabs: All / Unread / Groups / Stories
 *   5. Archive entry (visible when at least one chat is archived)
 *   6. "Закреплены" section header if any pinned
 *   7. Pinned rows (with SwipeRow gestures)
 *   8. Gap
 *   9. Regular rows (with SwipeRow gestures)
 *
 * Interaction state that tracks per-row overrides (pin/mute/archive/read) is hoisted
 * into [ChatListState], so the Archive screen and this screen share the same sources
 * of truth — toggling an action here immediately removes the chat from the list and
 * surfaces it in Archive.
 */
@Composable
fun ChatListScreen(
    state             : ChatListState,
    onOpenChat        : (String) -> Unit,
    onOpenSettings    : () -> Unit,
    onOpenNewChat     : () -> Unit,
    onOpenStories     : () -> Unit,
    onOpenCamera      : () -> Unit,
    onOpenBots        : () -> Unit,
    onOpenArchive     : () -> Unit,
    /** Id of the chat currently shown in the main pane (if any) — used to highlight
     *  that row in the sidebar so the user always sees which conversation is active. */
    selectedChatId    : String? = null,
) {
    val colors = KotoTheme.colors
    val spacing = KotoTheme.spacing
    val typography = KotoTheme.typography

    val vm: ChatListViewModel = koinInject()
    val activeChats by vm.entries.collectAsState()
    // Local DB query (selectAllConversations) already filters is_archived=0,
    // so [activeChats] is the live "non-archived" list. The Archive screen has
    // its own query path; we don't expose archived rows from this view-model.
    val archivedChats: List<ChatListEntry> = remember { emptyList() }

    val filtered by remember(activeChats) {
        derivedStateOf {
            activeChats.filter { chat ->
                when {
                    state.tab == ChatListTab.UNREAD && chat.unreadCount == 0 -> false
                    state.tab == ChatListTab.GROUPS                          -> false
                    state.query.isNotBlank() -> {
                        val q = state.query.lowercase()
                        chat.displayName.lowercase().contains(q) || chat.preview.lowercase().contains(q)
                    }
                    else -> true
                }
            }
        }
    }

    val pinnedList = filtered.filter { it.pinned }
    val restList   = filtered.filter { !it.pinned }

    // Mutual-exclusion map for open SwipeRow trays.
    val openRowMap: SnapshotStateMap<String, SwipeRowAnchor> = remember { mutableStateMapOf() }

    Column(
        modifier = Modifier.fillMaxSize().background(colors.background),
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(modifier = Modifier.clickable(onClick = onOpenSettings)) {
                Avatar(
                    initials = "Я",
                    color    = colors.accent,
                    size     = 36.dp,
                )
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(colors.accent)
                        .padding(2.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AnimatedIconButton(painter = svgIcon("bot_koto"),     contentDescription = "Боты",      onClick = onOpenBots)
                AnimatedIconButton(painter = svgIcon("camera_koto"),  contentDescription = "Камера",    onClick = onOpenCamera)
                AnimatedIconButton(painter = svgIcon("compose_koto"), contentDescription = "Новый чат", onClick = onOpenNewChat)
            }
        }

        // ── Large title + inline unread ───────────────────────────────────────
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 2.dp, bottom = 8.dp),
            verticalAlignment     = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text  = "Koto",
                style = typography.displayLarge,
                color = colors.text,
            )
            val totalUnread = activeChats.sumOf { it.unreadCount }
            Text(
                text     = "$totalUnread новых",
                style    = typography.bodyMedium,
                color    = colors.textSecondary,
                modifier = Modifier.padding(bottom = 5.dp),
            )
        }

        // ── Search ────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .height(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(imageVector = KotoIcons.Search, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(17.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (state.query.isEmpty()) {
                        Text(
                            text  = "Поиск по чатам и контактам",
                            style = typography.bodyLarge,
                            color = colors.textSecondary,
                        )
                    }
                    BasicTextField(
                        value           = state.query,
                        onValueChange   = { state.query = it },
                        singleLine      = true,
                        textStyle       = typography.bodyLarge.copy(color = colors.text),
                        cursorBrush     = androidx.compose.ui.graphics.SolidColor(colors.accent),
                        modifier        = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions.Default,
                    )
                }
            }
        }

        // ── Tabs ──────────────────────────────────────────────────────────────
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TabChip(label = "Все",            selected = state.tab == ChatListTab.ALL,    onClick = { state.tab = ChatListTab.ALL })
            TabChip(label = "Непрочитанные",  selected = state.tab == ChatListTab.UNREAD, onClick = { state.tab = ChatListTab.UNREAD })
            TabChip(label = "Группы",         selected = state.tab == ChatListTab.GROUPS, onClick = { state.tab = ChatListTab.GROUPS })
            TabChip(label = "Истории",        selected = false,                           onClick = onOpenStories)
        }

        // ── Folder strip (user-defined chat folders) ──────────────────────────
        val folders by vm.folders.collectAsState()
        val activeFolderId by vm.activeFolderId.collectAsState()
        if (folders.isNotEmpty()) {
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TabChip(
                    label    = "Все чаты",
                    selected = activeFolderId == ChatListViewModel.ALL_CHATS_ID,
                    onClick  = { vm.selectFolder(ChatListViewModel.ALL_CHATS_ID) },
                )
                folders.forEach { f ->
                    TabChip(
                        label    = f.name,
                        selected = activeFolderId == f.id,
                        onClick  = { vm.selectFolder(f.id) },
                    )
                }
            }
        }

        // ── List ──────────────────────────────────────────────────────────────
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
        ) {
            if (archivedChats.isNotEmpty()) {
                item(key = "__archive_entry") {
                    ArchiveEntryRow(
                        archivedChats = archivedChats,
                        onClick       = onOpenArchive,
                    )
                }
            }

            if (pinnedList.isNotEmpty()) {
                item(key = "__pinned_header") {
                    SectionHeader(text = "Закреплены")
                }
                items(pinnedList, key = { "p-${it.id}" }) { chat ->
                    ChatRowSwipeable(
                        chat       = chat,
                        vm         = vm,
                        openRowMap = openRowMap,
                        selected   = chat.id == selectedChatId,
                        onOpen     = { onOpenChat(chat.id) },
                    )
                }
                if (restList.isNotEmpty()) item(key = "__gap") { Spacer(Modifier.height(8.dp)) }
            }

            items(restList, key = { it.id }) { chat ->
                ChatRowSwipeable(
                    chat       = chat,
                    vm         = vm,
                    openRowMap = openRowMap,
                    selected   = chat.id == selectedChatId,
                    onOpen     = { onOpenChat(chat.id) },
                )
            }

            if (filtered.isEmpty()) {
                item("empty-state") {
                    val (title, msg, icon) = when {
                        state.query.isNotBlank()              -> Triple("Ничего не найдено", "Попробуйте другой запрос или Koto ID", KotoIcons.Search)
                        state.tab == ChatListTab.UNREAD       -> Triple("Всё прочитано", "Новые сообщения появятся здесь", KotoIcons.CheckCircle)
                        state.tab == ChatListTab.GROUPS       -> Triple("Нет групп", "Создайте группу, чтобы общаться вместе", KotoIcons.User)
                        archivedChats.isNotEmpty()            -> Triple("Активных чатов нет", "Архив доступен сверху списка", KotoIcons.Archive)
                        else                                  -> Triple("Пока тихо", "Начните новый чат — кнопка ✏ сверху", KotoIcons.Pencil)
                    }
                    EmptyState(
                        icon     = icon,
                        title    = title,
                        message  = msg,
                        modifier = Modifier.padding(vertical = 40.dp),
                    )
                }
            }

            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}


@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        targetValue = if (selected) KotoTheme.colors.accent else KotoTheme.colors.surface,
        label       = "tab-bg",
    )
    val fg by animateColorAsState(
        targetValue = if (selected) KotoTheme.colors.onAccent else KotoTheme.colors.text,
        label       = "tab-fg",
    )
    Box(
        modifier        = Modifier
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text  = label,
            color = fg,
            style = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 4.dp)) {
        Text(
            text  = text.uppercase(),
            style = KotoTheme.typography.labelSmall,
            color = KotoTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun ArchiveEntryRow(
    archivedChats : List<ChatListEntry>,
    onClick       : () -> Unit,
) {
    val colors = KotoTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(if (colors.isLight) Color(0xFFEEF0F3) else Color(0xFF1F1F22)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = KotoIcons.Archive, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = "Архив",
                style = KotoTheme.typography.titleMedium,
                color = colors.text,
            )
            val names = archivedChats.take(2).joinToString(", ") { it.displayName }
            val suffix = if (archivedChats.size > 2) " и ещё ${archivedChats.size - 2}" else ""
            Text(
                text     = names + suffix,
                style    = KotoTheme.typography.bodySmall,
                color    = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val totalUnread = archivedChats.sumOf { it.unreadCount }
        if (totalUnread > 0) {
            Box(
                modifier        = Modifier
                    .widthIn(min = 20.dp)
                    .height(20.dp)
                    .clip(CircleShape)
                    .background(colors.textSecondary)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "$totalUnread", style = KotoTheme.typography.labelSmall, color = Color.White)
            }
        }
        Icon(imageVector = KotoIcons.ChevronRight, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
    }
}

/**
 * Swipeable wrapper — one row in the list. Pin / mute / archive actions go
 * through [ChatListViewModel] so the change is persisted server- and disk-
 * side and propagates back through [ChatListViewModel.entries].
 */
@Composable
private fun ChatRowSwipeable(
    chat       : ChatListEntry,
    vm         : ChatListViewModel,
    openRowMap : SnapshotStateMap<String, SwipeRowAnchor>,
    selected   : Boolean,
    onOpen     : () -> Unit,
) {
    val colors = KotoTheme.colors

    val leftActions = listOf(
        SwipeAction(
            id         = "pin",
            background = colors.warning,
            foreground = Color.White,
            icon       = KotoIcons.Pin,
            label      = if (chat.pinned) "Открепить" else "Закрепить",
            onAction   = { vm.setPinned(chat.id, !chat.pinned) },
        ),
        SwipeAction(
            id         = "mute",
            background = colors.muted,
            foreground = Color.White,
            icon       = if (chat.muted) KotoIcons.Bell else KotoIcons.Mute,
            label      = if (chat.muted) "Вкл. звук" else "Без звука",
            onAction   = { vm.setMuted(chat.id, !chat.muted) },
        ),
        SwipeAction(
            id           = "archive",
            background   = Color(0xFFC47A3A),
            foreground   = Color.White,
            icon         = KotoIcons.Archive,
            label        = "Архив",
            commitOnFull = true,
            onAction     = { vm.archive(chat.id) },
        ),
    )

    SwipeRow(
        id           = chat.id,
        leftActions  = leftActions,
        rightActions = emptyList(),       // mark-as-read is server-driven, no swipe override
        openRowId    = openRowMap,
        onOpen       = onOpen,
        rowBackground = if (selected) colors.surface else colors.background,
        rowHeight    = 72.dp,
    ) {
        ChatRowContent(chat = chat)
    }
}

/** Public for re-use by [ArchiveScreen]. */
@Composable
fun ChatRowPublic(chat: ChatListEntry) = ChatRowContent(chat)

@Composable
private fun ChatRowContent(chat: ChatListEntry) {
    val colors  = KotoTheme.colors

    Row(
        modifier              = Modifier
            .fillMaxSize()
            .padding(start = 20.dp, end = 16.dp)
            .padding(vertical = 10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(
            initials = chat.initials,
            color    = chat.avatarColor,
            size     = 52.dp,
            online   = chat.isOnline,
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text       = chat.displayName,
                    style      = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
                    color      = colors.text,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    modifier   = Modifier.widthIn(max = 200.dp),
                )
                if (chat.muted) {
                    Icon(imageVector = KotoIcons.Mute, contentDescription = "muted", tint = colors.textSecondary, modifier = Modifier.size(13.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text  = chat.timeLabel,
                    style = KotoTheme.typography.labelMedium,
                    color = if (chat.unreadCount > 0) colors.accent else colors.textSecondary,
                )
            }
            Spacer(Modifier.height(3.dp))
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text       = chat.preview.ifBlank { "—" },
                    style      = TextStyle(fontSize = 14.sp),
                    color      = colors.textSecondary,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    modifier   = Modifier.weight(1f),
                )
                if (chat.unreadCount > 0) {
                    Box(
                        modifier         = Modifier
                            .widthIn(min = 22.dp)
                            .height(22.dp)
                            .clip(CircleShape)
                            .background(if (chat.muted) colors.textSecondary else colors.accent)
                            .padding(horizontal = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text  = chat.unreadCount.toString(),
                            style = KotoTheme.typography.labelMedium,
                            color = Color.White,
                        )
                    }
                } else if (chat.pinned) {
                    Icon(imageVector = KotoIcons.Pin, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

// ── State object ────────────────────────────────────────────────────────────

enum class ChatListTab { ALL, UNREAD, GROUPS }

class ChatListState {
    var tab    : ChatListTab by mutableStateOf(ChatListTab.ALL)
    var query  : String      by mutableStateOf("")
    val archived        : SnapshotStateMap<String, Boolean> = mutableStateMapOf()
    val muted           : SnapshotStateMap<String, Boolean> = mutableStateMapOf()
    val pinned          : SnapshotStateMap<String, Boolean> = mutableStateMapOf()
    val readOverrides   : SnapshotStateMap<String, String>  = mutableStateMapOf()
}

