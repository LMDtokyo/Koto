package run.koto.desktop.ui.screens.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import kotlinx.collections.immutable.toImmutableList
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import org.koin.compose.koinInject
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import run.koto.desktop.ui.components.atoms.MessageStatus
import run.koto.desktop.ui.components.atoms.TypingDots
import run.koto.desktop.ui.icons.KotoIcons
import run.koto.desktop.ui.theme.KotoTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Conversation screen — main-pane content when a chat is selected.
 *
 * Layout (top→bottom):
 *   1. [ConversationHeader] — peer avatar, name, presence, call/video buttons
 *   2. Messages list — LazyColumn with E2EE banner, optional ephemeral banner,
 *      grouped bubbles, typing indicator
 *   3. [Composer] — attach + input + mic↔send morph
 *
 * State held locally (UI mock layer): outgoing messages, draft, reaction target.
 * All state is re-keyed on [chatId] so switching chats resets the conversation.
 * When the real data layer lands (Phase 4) this composable becomes a thin view
 * over a ChatViewModel — the messages StateFlow replaces the mutableStateList
 * and `send` dispatches into the repository.
 */
@Composable
fun ConversationScreen(
    chatId        : String,
    ephemeralOn   : Boolean,
    onOpenContact : () -> Unit,
    onOpenCall    : () -> Unit,
    onOpenVideo   : () -> Unit,
    onOpenAttach  : () -> Unit,
    onOpenEmoji   : () -> Unit,
    onOpenEphemeralSheet : () -> Unit,
    emojiPicked   : String?  = null,
    onEmojiConsumed : () -> Unit = {},
    modifier      : Modifier = Modifier,
) {
    val colors   = KotoTheme.colors
    val vm: ConversationViewModel = koinInject()

    LaunchedEffect(chatId) { vm.open(chatId) }

    val conversation by vm.current.collectAsState()
    val messageList  by vm.messages.collectAsState()
    val sendError    by vm.sendError.collectAsState()
    val peerName    = conversation?.displayName ?: "Koto User"
    val isBot       = false

    // The current user's account id — needed to mark reactions as "mine"
    // for the chip's filled-vs-outlined state.
    val authRepo: run.koto.desktop.domain.repository.AuthRepository = org.koin.compose.koinInject()
    val session by authRepo.session.collectAsState(initial = null)
    val meId = session?.accountId.orEmpty()

    // Map domain messages → UI bubbles. Reactions are grouped by emoji with
    // a `mine` flag derived from whether [meId] is among the actors. Reply
    // parents are resolved by id-lookup against the loaded list — out-of-
    // order replies (parent not yet synced) gracefully degrade to "no preview".
    val messages = remember(messageList, meId) {
        val byId = messageList.associateBy { it.id }
        messageList.map { it.toUiMessage(peerName, meId, byId) }
    }

    var draft               by remember(chatId) { mutableStateOf("") }
    var reactionFor         by remember(chatId) { mutableStateOf<ReactionTarget?>(null) }
    var composerCtx         by remember(chatId) { mutableStateOf<ComposerContext?>(null) }
    var forwardingMessageId by remember(chatId) { mutableStateOf<String?>(null) }
    var searchOpen   by remember(chatId) { mutableStateOf(false) }
    var searchQuery  by remember(chatId) { mutableStateOf("") }
    val peerTyping   = false   // typing indicator wires up once gateway forwards typing events
    val clipboard    = androidx.compose.ui.platform.LocalClipboardManager.current

    LaunchedEffect(emojiPicked) {
        if (emojiPicked != null) {
            draft += emojiPicked
            onEmojiConsumed()
        }
    }

    val scope     = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when the message list grows.
    LaunchedEffect(chatId) {
        snapshotFlow { messages.size }
            .distinctUntilChanged()
            .collect {
                if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
            }
    }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty()) return
        val ctx = composerCtx
        when (ctx?.mode) {
            ComposerContext.Mode.Edit  -> vm.editMessage(ctx.messageId, text)
            ComposerContext.Mode.Reply -> vm.send(text, replyToId = ctx.messageId)
            null                       -> vm.send(text)
        }
        draft = ""
        composerCtx = null
    }

    val grouped by remember(messages, searchQuery) {
        derivedStateOf {
            val q = searchQuery.trim().lowercase()
            val source = if (q.isBlank()) messages
                         else messages.filter { it.text.lowercase().contains(q) }
            source.withGrouping()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Header ──────────────────────────────────────────────────────
            ConversationHeader(
                name           = peerName,
                initials       = peerInitials(peerName),
                avatarColor    = peerColor(conversation?.peerAccountId.orEmpty()),
                statusLine     = if (conversation?.isOnline == true) "в сети" else "не в сети",
                isOnline       = conversation?.isOnline == true,
                ephemeralOn    = ephemeralOn,
                onOpenContact  = onOpenContact,
                onOpenCall     = onOpenCall,
                onOpenVideo    = onOpenVideo,
                onOpenSearch   = { searchOpen = !searchOpen; if (!searchOpen) searchQuery = "" },
                onOpenMore     = onOpenEphemeralSheet,
            )

            if (searchOpen) {
                SearchBar(
                    query   = searchQuery,
                    onQuery = { searchQuery = it },
                    onClose = { searchOpen = false; searchQuery = "" },
                    matches = grouped.size,
                )
            }

            // Pinned messages bar — shows the most recent pin with a one-line
            // preview; tap scrolls to it. Hidden when no pins exist.
            val pinnedTop = remember(messages) { messages.firstOrNull { it.pinned } }
            if (pinnedTop != null) {
                PinnedBar(
                    pinned   = pinnedTop,
                    onClick  = {
                        val idx = messages.indexOf(pinnedTop)
                        if (idx >= 0) scope.launch { listState.animateScrollToItem(idx) }
                    },
                    onUnpin  = { vm.togglePin(pinnedTop.id, true) },
                )
            }

            val listBrush = remember(colors.background, colors.isLight) {
                messagesAreaBrush(colors.background, colors.isLight)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(listBrush),
            ) {
                LazyColumn(
                    state                = listState,
                    modifier             = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    contentPadding        = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
                    verticalArrangement   = Arrangement.spacedBy(0.dp),
                ) {
                    // E2EE banner
                    item("e2ee-banner") {
                        E2eeBanner(
                            isBot       = isBot,
                            text        = if (isBot)
                                "Боты получают только то, что вы им пишете"
                            else
                                "Сообщения зашифрованы end-to-end",
                        )
                    }

                    // Ephemeral banner
                    if (ephemeralOn) {
                        item("ephemeral-banner") { EphemeralBanner() }
                    }

                    items(
                        items       = grouped,
                        key         = { it.message.id },
                        contentType = { if (it.message.typing) "typing" else "bubble" },
                    ) { g ->
                        val msg = g.message
                        if (msg.typing) {
                            // No animateItem(): placement animation runs on every window resize relayout
                            // and makes the list feel jittery (Lazy list item bounds change continuously).
                            PeerTypingRow()
                        } else {
                            MessageBubble(
                                grouped       = g,
                                modifier      = Modifier,
                                onLongPress   = { m, b ->
                                    reactionFor = ReactionTarget(message = m, bounds = b)
                                },
                                onToggleReact = { m, emoji -> vm.toggleReaction(m.id, emoji) },
                                onReplyClick  = { targetId ->
                                    val idx = messages.indexOfFirst { it.id == targetId }
                                    if (idx >= 0) {
                                        scope.launch { listState.animateScrollToItem(idx) }
                                    }
                                },
                                hidden        = reactionFor?.message?.id == msg.id,
                            )
                        }
                    }

                    // Typing indicator (when peer typing outside the seed data)
                    if (peerTyping) {
                        item("peer-typing") { PeerTypingRow() }
                    }

                    // Bottom breathing room
                    item("tail") { Spacer(Modifier.height(8.dp)) }
                }
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.separator))
            Composer(
                draft         = draft,
                placeholder   = if (isBot) "Написать $peerName…" else "Сообщение",
                onDraftChange = { draft = it },
                onSend        = { send() },
                onOpenAttach  = onOpenAttach,
                onOpenEmoji   = onOpenEmoji,
                context       = composerCtx,
                onCancelContext = {
                    composerCtx = null
                    draft = ""
                },
            )
        }

        reactionFor?.let { target ->
            ReactionOverlay(
                target         = target,
                onToggleReact  = { emoji ->
                    vm.toggleReaction(target.message.id, emoji)
                    reactionFor = null
                },
                onAction       = { action ->
                    val msg = target.message
                    when (action) {
                        BubbleAction.Copy   -> clipboard.setText(androidx.compose.ui.text.AnnotatedString(msg.text))
                        BubbleAction.Reply  -> composerCtx = ComposerContext(
                            mode         = ComposerContext.Mode.Reply,
                            authorOrSelf = if (msg.self) "себе" else (msg.author ?: peerName),
                            text         = msg.text,
                            messageId    = msg.id,
                        )
                        BubbleAction.Edit   -> {
                            // Switch the composer to Edit mode keyed to the
                            // selected message id. send() routes to vm.editMessage
                            // when the context's mode is Edit, which triggers a
                            // PATCH end-to-end through the server.
                            draft = msg.text
                            composerCtx = ComposerContext(
                                mode         = ComposerContext.Mode.Edit,
                                authorOrSelf = "себе",
                                text         = msg.text,
                                messageId    = msg.id,
                            )
                        }
                        BubbleAction.Delete -> vm.delete(msg.id)
                        BubbleAction.Forward -> forwardingMessageId = msg.id
                        BubbleAction.Pin     -> vm.togglePin(msg.id, msg.pinned)
                        BubbleAction.Report  -> {}
                    }
                    reactionFor = null
                },
                onDismiss      = { reactionFor = null },
            )
        }

        forwardingMessageId?.let { mid ->
            ForwardPicker(
                excludeConvId = chatId,
                onPick        = { destId ->
                    vm.forward(mid, listOf(destId))
                    forwardingMessageId = null
                },
                onDismiss     = { forwardingMessageId = null },
            )
        }
    }
}

// ─── Helpers ───────────────────────────────────────────────────────────────

/** Map a [run.koto.desktop.domain.model.Message] to the UI bubble shape.
 *  Reactions are grouped by emoji — the chip's `mine` flag is set when [meId]
 *  appears in the actor set for that emoji. [byId] resolves a [replyToId]
 *  pointer to the actual parent message so the bubble can render a preview. */
private fun run.koto.desktop.domain.model.Message.toUiMessage(
    peerName: String,
    meId: String,
    byId: Map<String, run.koto.desktop.domain.model.Message>,
): Message {
    val status = when {
        !isOutgoing -> null
        read        -> MessageStatus.READ
        delivered   -> MessageStatus.DELIVERED
        else        -> MessageStatus.SENT
    }
    val grouped: kotlinx.collections.immutable.ImmutableList<Reaction> =
        if (reactions.isEmpty()) kotlinx.collections.immutable.persistentListOf()
        else kotlinx.collections.immutable.persistentListOf<Reaction>().builder().apply {
            reactions.groupBy { it.emoji }.forEach { (emoji, list) ->
                add(Reaction(
                    emoji = emoji,
                    count = list.size,
                    mine  = list.any { it.actorId == meId },
                ))
            }
        }.build()
    val reply = replyToId?.let { rid ->
        val parent = byId[rid]
        if (parent != null) {
            ReplyPreview(
                targetId = parent.id,
                author   = if (parent.isOutgoing) "Вы" else peerName,
                text     = parent.plaintext,
            )
        } else {
            // Parent not in the loaded window (history pagination, or the
            // sender's reply landed before we synced its target). Graceful
            // fallback so the user still sees "это ответ".
            ReplyPreview(targetId = rid, author = "—", text = "сообщение недоступно")
        }
    }
    return Message(
        id        = id,
        self      = isOutgoing,
        text      = plaintext,
        time      = formatHHmm(sentAt),
        status    = status,
        author    = if (isOutgoing) null else peerName,
        reactions = grouped,
        replyTo   = reply,
        edited    = editedAt != null,
        forwardedFromLabel = forwardedFrom?.let { abbreviateAccountId(it) },
        pinned    = pinned,
    )
}

private fun abbreviateAccountId(id: String): String =
    if (id.length > 12) id.take(6) + "…" + id.takeLast(4) else id

private val hhmmFmt = DateTimeFormatter.ofPattern("HH:mm")
private fun formatHHmm(epochMs: Long): String =
    if (epochMs <= 0) "" else Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(hhmmFmt)

private fun peerInitials(name: String): String =
    name.split(' ', limit = 3)
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifBlank { "?" }

private val peerPalette = listOf(
    Color(0xFFFF6B35), Color(0xFF7C5CFF), Color(0xFF00A676), Color(0xFFF0B400),
    Color(0xFFE74C6F), Color(0xFF3276FF), Color(0xFF13B3A5), Color(0xFFFF7A9A),
)

private fun peerColor(accountId: String): Color {
    if (accountId.isEmpty()) return peerPalette[0]
    var h = 0
    for (c in accountId) h = (h * 31 + c.code) and 0x7fffffff
    return peerPalette[h % peerPalette.size]
}

private fun messagesAreaBrush(bg: Color, isLight: Boolean): Brush {
    // Soft radial from a warm top-center anchor — matches the mockup's
    // `radial-gradient(1200px 600px at 50% -100px, ...)` halo.
    val tint = if (isLight) Color(0xFFFFF8F4) else Color(0xFF181820)
    return Brush.radialGradient(
        colors  = listOf(tint, bg),
        radius  = 900f,
    )
}

// ─── In-scroll decorations ──────────────────────────────────────────────────

@Composable
private fun E2eeBanner(isBot: Boolean, text: String) {
    val colors = KotoTheme.colors
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier              = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(colors.accent.copy(alpha = 0.12f))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector        = KotoIcons.Lock,
                contentDescription = null,
                tint               = colors.accent,
                modifier           = Modifier.size(12.dp),
            )
            Text(
                text  = text,
                style = KotoTheme.typography.labelMedium,
                color = colors.accent,
            )
        }
    }
}

@Composable
private fun EphemeralBanner() {
    val colors = KotoTheme.colors
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier              = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surface)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector        = KotoIcons.Timer,
                contentDescription = null,
                tint               = colors.textSecondary,
                modifier           = Modifier.size(12.dp),
            )
            Text(
                text  = "Вы включили исчезающие сообщения · 1 час",
                style = KotoTheme.typography.labelMedium,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun PeerTypingRow(modifier: Modifier = Modifier) {
    val colors = KotoTheme.colors
    Row(
        modifier             = modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp))
                .background(colors.bubblePeer)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            TypingDots(color = colors.accent, size = 6.dp)
        }
    }
}

@Composable
private fun SearchBar(
    query   : String,
    onQuery : (String) -> Unit,
    onClose : () -> Unit,
    matches : Int,
) {
    val colors = KotoTheme.colors
    val focus  = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        androidx.compose.material3.Icon(
            imageVector        = run.koto.desktop.ui.icons.KotoIcons.Search,
            contentDescription = null,
            tint               = colors.textSecondary,
            modifier           = Modifier.size(16.dp),
        )
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text  = "Поиск в этом чате",
                    style = KotoTheme.typography.bodyMedium,
                    color = colors.textTertiary,
                )
            }
            androidx.compose.foundation.text.BasicTextField(
                value         = query,
                onValueChange = onQuery,
                singleLine    = true,
                textStyle     = androidx.compose.material3.LocalTextStyle.current.merge(
                    KotoTheme.typography.bodyMedium.copy(color = colors.text),
                ),
                cursorBrush   = androidx.compose.ui.graphics.SolidColor(colors.accent),
                modifier      = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        if (query.isNotBlank()) {
            Text(
                text  = "$matches",
                style = KotoTheme.typography.labelMedium,
                color = colors.textSecondary,
            )
        }
        Box(
            modifier         = Modifier
                .size(24.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                imageVector        = run.koto.desktop.ui.icons.KotoIcons.Close,
                contentDescription = "Закрыть поиск",
                tint               = colors.textSecondary,
                modifier           = Modifier.size(14.dp),
            )
        }
    }
}
