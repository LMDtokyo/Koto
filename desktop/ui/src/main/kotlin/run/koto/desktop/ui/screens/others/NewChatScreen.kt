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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import run.koto.desktop.ui.components.atoms.Avatar
import run.koto.desktop.ui.icons.KotoIcons
import run.koto.desktop.ui.theme.KotoTheme

/**
 * New Chat — find a peer by Koto ID (64-char hex, with or without the legacy
 * `05` prefix) or by `@username`. The view-model debounces username lookups
 * so the user doesn't trigger a request per keystroke.
 *
 * On confirm we call [run.koto.desktop.domain.repository.ConversationRepository.createDirect]
 * — idempotent server-side — and navigate the host nav stack to the new
 * conversation id.
 */
@Composable
fun NewChatScreen(
    onBack        : () -> Unit,
    onOpenChat    : (String) -> Unit,
    onCreateGroup : () -> Unit,
    ownAccountId  : String,
) {
    val colors = KotoTheme.colors
    val vm: NewChatViewModel = koinInject()
    val state by vm.state.collectAsState()
    var inviteOpen by remember { mutableStateOf(false) }
    var qrOpen     by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { vm.close() } }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top bar ───────────────────────────────────────────────────
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
                    Icon(
                        imageVector        = KotoIcons.Back,
                        contentDescription = null,
                        tint               = colors.accent,
                        modifier           = Modifier.size(22.dp),
                    )
                    Text(text = "Отмена", style = KotoTheme.typography.titleMedium, color = colors.accent)
                }
                Text(
                    text       = "Новый чат",
                    style      = KotoTheme.typography.titleLarge,
                    color      = colors.text,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier.weight(1f),
                    textAlign  = TextAlign.Center,
                )
                Spacer(Modifier.size(90.dp))
            }

            // ── Search field ──────────────────────────────────────────────
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surface)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector        = KotoIcons.Search,
                        contentDescription = null,
                        tint               = colors.textSecondary,
                        modifier           = Modifier.size(17.dp),
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        if (state.query.isEmpty()) {
                            Text(
                                text  = "@username или Koto ID",
                                style = KotoTheme.typography.bodyMedium,
                                color = colors.textTertiary,
                            )
                        }
                        BasicTextField(
                            value         = state.query,
                            onValueChange = vm::onQueryChange,
                            singleLine    = true,
                            textStyle     = LocalTextStyle.current.merge(
                                KotoTheme.typography.bodyMedium.copy(color = colors.text),
                            ),
                            cursorBrush   = SolidColor(colors.accent),
                            modifier      = Modifier.fillMaxWidth(),
                        )
                    }
                    if (state.query.isNotEmpty()) {
                        Box(
                            modifier          = Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication        = ripple(bounded = true),
                                    onClick           = { vm.onQueryChange("") },
                                ),
                            contentAlignment  = Alignment.Center,
                        ) {
                            Icon(
                                imageVector        = KotoIcons.Close,
                                contentDescription = null,
                                tint               = colors.textSecondary,
                                modifier           = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Result card driven by the resolution state ────────────────
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                when (val r = state.resolution) {
                    NewChatViewModel.Resolution.Idle ->
                        ActionTilesRow(
                            onNewGroup = onCreateGroup,
                            onScanQr   = { qrOpen     = true },
                            onInvite   = { inviteOpen = true },
                        )

                    NewChatViewModel.Resolution.UsernameSeed ->
                        HintCard(text = "Подождите — ищем @${state.query.trimStart('@')}…")

                    NewChatViewModel.Resolution.Resolving ->
                        HintCard(text = "Ищем профиль…")

                    NewChatViewModel.Resolution.NotFound ->
                        HintCard(text = "Никого не нашли с таким именем.", warning = true)

                    is NewChatViewModel.Resolution.Invalid ->
                        HintCard(text = r.reason, warning = true)

                    is NewChatViewModel.Resolution.KotoId ->
                        ResolvedCard(
                            title       = "Koto ID профиль",
                            subtitle    = "${r.accountId.take(12)}…${r.accountId.takeLast(8)}",
                            initials    = "?",
                            color       = colors.warning,
                            note        = "Этот Koto ID ещё не в ваших контактах. Сравните номер безопасности при первой переписке.",
                            actionLabel = if (state.creating) "создаём…" else "Начать чат",
                            actionEnabled = !state.creating,
                            onAction    = { vm.createChatWith(r.accountId, onOpenChat) },
                        )

                    is NewChatViewModel.Resolution.Resolved ->
                        ResolvedCard(
                            title       = r.displayName,
                            subtitle    = "@${r.username}",
                            initials    = initialsOf(r.displayName),
                            color       = peerColor(r.accountId),
                            note        = null,
                            actionLabel = if (state.creating) "создаём…" else "Начать чат",
                            actionEnabled = !state.creating,
                            onAction    = { vm.createChatWith(r.accountId, onOpenChat) },
                        )
                }
            }

            // Bottom helper tile — shown only on the empty / idle screen so the
            // resolved card stays the focus when the user types a peer.
            if (state.resolution == NewChatViewModel.Resolution.Idle) {
                Spacer(Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    HintCard(text = "Введите @username или Koto ID собеседника. Поддерживается paste 64-символьного hex или 66-символьного формата `05…`.")
                }
            }

            if (state.createError != null) {
                Text(
                    text     = state.createError.orEmpty(),
                    style    = KotoTheme.typography.bodySmall,
                    color    = colors.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }

        if (inviteOpen) InviteSheet(accountId = ownAccountId, onDismiss = { inviteOpen = false })
        if (qrOpen)     QrSheet    (accountId = ownAccountId, onDismiss = { qrOpen     = false })
    }
}

// ─── Sub-components ────────────────────────────────────────────────────────

/**
 * Row of three quick-action tiles shown on the idle screen: New Group,
 * Scan QR, Invite. None are wired to backend yet — the underlying features
 * (group chats, camera scan, share sheet) are out of scope; the tiles stay
 * visible so the layout matches the design reference and discoverability
 * is preserved for when each lands.
 */
@Composable
private fun ActionTilesRow(
    onNewGroup : () -> Unit,
    onScanQr   : () -> Unit,
    onInvite   : () -> Unit,
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ActionTile(icon = KotoIcons.Plus,    label = "Новая группа",   onClick = onNewGroup, modifier = Modifier.weight(1f))
        ActionTile(icon = KotoIcons.Qr,      label = "Мой QR",         onClick = onScanQr,   modifier = Modifier.weight(1f))
        ActionTile(icon = KotoIcons.Forward, label = "Пригласить",     onClick = onInvite,   modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ActionTile(
    icon    : androidx.compose.ui.graphics.vector.ImageVector,
    label   : String,
    onClick : () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KotoTheme.colors
    Column(
        modifier            = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = ripple(bounded = true),
                onClick           = onClick,
            )
            .padding(vertical = 16.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = colors.accent,
            modifier           = Modifier.size(22.dp),
        )
        Text(
            text       = label,
            style      = KotoTheme.typography.labelLarge,
            color      = colors.text,
            fontWeight = FontWeight.Medium,
            textAlign  = TextAlign.Center,
        )
    }
}

@Composable
private fun HintCard(text: String, warning: Boolean = false) {
    val colors = KotoTheme.colors
    val tint   = if (warning) colors.warning else colors.textSecondary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(
                width = if (warning) 1.dp else 0.dp,
                color = if (warning) colors.warning.copy(alpha = 0.35f) else Color.Transparent,
                shape = RoundedCornerShape(14.dp),
            )
            .padding(14.dp),
    ) {
        Text(
            text  = text,
            style = KotoTheme.typography.bodyMedium,
            color = tint,
        )
    }
}

@Composable
private fun ResolvedCard(
    title         : String,
    subtitle      : String,
    initials      : String,
    color         : Color,
    note          : String?,
    actionLabel   : String,
    actionEnabled : Boolean,
    onAction      : () -> Unit,
) {
    val colors = KotoTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(1.dp, colors.separator, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(initials = initials, color = color, size = 44.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title,    style = KotoTheme.typography.titleSmall, color = colors.text, fontWeight = FontWeight.SemiBold)
                Text(text = subtitle, style = KotoTheme.typography.monoSmall,  color = colors.textSecondary, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (note != null) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.warning.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment     = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector        = KotoIcons.Lock,
                    contentDescription = null,
                    tint               = colors.warning,
                    modifier           = Modifier.size(12.dp).padding(top = 2.dp),
                )
                Text(text = note, style = KotoTheme.typography.labelMedium, color = colors.warning)
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier         = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.accent.copy(alpha = if (actionEnabled) 1f else 0.4f))
                .clickable(
                    enabled           = actionEnabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = ripple(bounded = true),
                    onClick           = onAction,
                )
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text       = actionLabel,
                style      = KotoTheme.typography.titleSmall,
                color      = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ─── Local helpers ────────────────────────────────────────────────────────

private fun initialsOf(name: String): String =
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
