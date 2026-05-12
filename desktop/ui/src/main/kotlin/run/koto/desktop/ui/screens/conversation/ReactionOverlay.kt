package run.koto.desktop.ui.screens.conversation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import run.koto.desktop.ui.icons.KotoIcons
import run.koto.desktop.ui.theme.KotoEasing
import run.koto.desktop.ui.theme.KotoTheme

/**
 * Full-pane overlay shown when the user long-presses a message. Renders:
 *   1. Dim scrim (tap anywhere → dismiss)
 *   2. Reaction picker — 6 emoji in a pill that arcs in from `scale(.2)` (reactPickerIn)
 *   3. A frozen visual copy of the bubble at its original on-screen bounds
 *   4. Action menu — iOS-style 5-row floating card (actionMenuIn + actionMenuIn delay 40 ms)
 *
 * The picker/menu are horizontally anchored to the bubble's edge (left for peer,
 * right for self) so the visual connection to the source message reads clearly.
 */
@Composable
fun ReactionOverlay(
    target          : ReactionTarget,
    onToggleReact   : (String) -> Unit,
    onAction        : (BubbleAction) -> Unit,
    onDismiss       : () -> Unit,
) {
    val colors = KotoTheme.colors
    val msg    = target.message
    val self   = msg.self

    val actions = if (self) {
        listOf(
            BubbleActionItem(BubbleAction.Reply,    "Ответить",     KotoIcons.Reply,   false),
            BubbleActionItem(BubbleAction.Forward,  "Переслать",    KotoIcons.Forward, false),
            BubbleActionItem(BubbleAction.Copy,     "Копировать",   KotoIcons.Copy,    false),
            BubbleActionItem(BubbleAction.Edit,     "Редактировать", KotoIcons.Pencil,  false),
            BubbleActionItem(BubbleAction.Delete,   "Удалить",      KotoIcons.Trash,   true),
        )
    } else {
        listOf(
            BubbleActionItem(BubbleAction.Reply,    "Ответить",     KotoIcons.Reply,   false),
            BubbleActionItem(BubbleAction.Forward,  "Переслать",    KotoIcons.Forward, false),
            BubbleActionItem(BubbleAction.Copy,     "Копировать",   KotoIcons.Copy,    false),
            BubbleActionItem(BubbleAction.Pin,      "Закрепить",    KotoIcons.Pin,     false),
            BubbleActionItem(BubbleAction.Report,   "Сообщить",     KotoIcons.Alert,   true),
        )
    }

    // Scrim + container opacity
    val scrimAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) { scrimAlpha.animateTo(1f, tween(180)) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f * scrimAlpha.value))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = onDismiss,
            ),
    ) {
        // ── Frozen bubble ───────────────────────────────────────────────────
        // Absolute position via density-scaled offsets. `onGloballyPositioned` on the
        // original bubble captured px; we translate back to dp for placement.
        val density = LocalDensity.current
        val xDp = with(density) { target.bounds.x.toDp() }
        val yDp = with(density) { target.bounds.y.toDp() }
        val wDp = with(density) { target.bounds.width.toDp() }

        // Bubble-lift animation: 1.0 → 1.04 → 1.02 over 260ms
        val lift = remember { Animatable(1f) }
        LaunchedEffect(Unit) {
            lift.animateTo(1.04f, tween(130, easing = KotoEasing))
            lift.animateTo(1.02f, tween(130, easing = KotoEasing))
        }

        Box(
            modifier = Modifier
                .padding(start = xDp, top = yDp)
                .width(wDp)
                .graphicsLayer {
                    scaleX          = lift.value
                    scaleY          = lift.value
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                        pivotFractionX = if (self) 1f else 0f,
                        pivotFractionY = 0.5f,
                    )
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = null,
                    onClick           = {},        // swallow: don't dismiss
                ),
        ) {
            FrozenBubble(message = msg)
        }

        // ── Reaction picker ─────────────────────────────────────────────────
        val pickerOffsetY = yDp - 60.dp
        Box(
            modifier = Modifier
                .padding(
                    start = if (self) 0.dp else xDp.coerceAtLeast(16.dp),
                    end   = if (self) 16.dp else 0.dp,
                    top   = pickerOffsetY.coerceAtLeast(16.dp),
                )
                .align(if (self) Alignment.TopEnd else Alignment.TopStart),
        ) {
            ReactionPicker(
                selected      = msg.reactions.filter { it.mine }.map { it.emoji }.toSet(),
                onEmoji       = onToggleReact,
                self          = self,
            )
        }

        // ── Action menu ─────────────────────────────────────────────────────
        val menuOffsetY = yDp + with(density) { target.bounds.height.toDp() } + 12.dp
        Box(
            modifier = Modifier
                .padding(
                    start = if (self) 0.dp else xDp.coerceAtLeast(16.dp),
                    end   = if (self) 16.dp else 0.dp,
                    top   = menuOffsetY.coerceAtMost(500.dp),
                )
                .align(if (self) Alignment.TopEnd else Alignment.TopStart),
        ) {
            ActionMenu(
                actions  = actions,
                self     = self,
                onSelect = onAction,
            )
        }
    }
}

// ─── Reaction picker pill ──────────────────────────────────────────────────

@Composable
private fun ReactionPicker(
    selected  : Set<String>,
    onEmoji   : (String) -> Unit,
    self      : Boolean,
) {
    val colors = KotoTheme.colors

    // Container pop: scale(.2) translateY(+20) → scale(1.06, -2) → scale(1)
    val containerScale = remember { Animatable(0.2f) }
    val containerAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        coroutineScope {
            launch { containerScale.animateTo(1.06f, tween(180, easing = KotoEasing)) }
            launch { containerAlpha.animateTo(1f,    tween(180, easing = KotoEasing)) }
        }
        containerScale.animateTo(1f, tween(120, easing = KotoEasing))
    }

    Row(
        modifier              = Modifier
            .graphicsLayer {
                scaleX          = containerScale.value
                scaleY          = containerScale.value
                alpha           = containerAlpha.value
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                    pivotFractionX = if (self) 1f else 0f,
                    pivotFractionY = 1f,
                )
            }
            .clip(RoundedCornerShape(999.dp))
            .background(colors.elevated.copy(alpha = 0.95f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = {},        // swallow clicks in the picker
            ),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        REACTION_EMOJIS.forEachIndexed { i, e ->
            EmojiButton(
                emoji     = e,
                selected  = e in selected,
                delayMs   = i * 30,
                onClick   = { onEmoji(e) },
            )
        }
    }
}

@Composable
private fun EmojiButton(
    emoji    : String,
    selected : Boolean,
    delayMs  : Int,
    onClick  : () -> Unit,
) {
    val colors = KotoTheme.colors
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        // emojiPop: 0 → 1.2 (70%) → 1
        pop.animateTo(1.2f, tween(220, easing = KotoEasing))
        pop.animateTo(1.0f, tween(100, easing = KotoEasing))
    }
    Box(
        modifier          = Modifier
            .size(38.dp)
            .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
            .clip(CircleShape)
            .background(if (selected) colors.accent.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = ripple(bounded = true, radius = 19.dp),
                onClick           = onClick,
            ),
        contentAlignment  = Alignment.Center,
    ) {
        Text(text = emoji, style = KotoTheme.typography.headlineMedium)
    }
}

// ─── Action menu ───────────────────────────────────────────────────────────

@Composable
private fun ActionMenu(
    actions  : List<BubbleActionItem>,
    self     : Boolean,
    onSelect : (BubbleAction) -> Unit,
) {
    val colors = KotoTheme.colors

    // actionMenuIn: scale .85 translateY -8 → scale 1 translateY 0, 220ms w/ 40ms delay
    val scaleA = remember { Animatable(0.85f) }
    val yA     = remember { Animatable(-8f) }
    val alphaA = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(40)
        coroutineScope {
            launch { scaleA.animateTo(1f, tween(220, easing = KotoEasing)) }
            launch { yA    .animateTo(0f, tween(220, easing = KotoEasing)) }
            launch { alphaA.animateTo(1f, tween(220, easing = KotoEasing)) }
        }
    }

    Column(
        modifier = Modifier
            .graphicsLayer {
                scaleX          = scaleA.value
                scaleY          = scaleA.value
                translationY    = yA.value
                alpha           = alphaA.value
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                    pivotFractionX = if (self) 1f else 0f,
                    pivotFractionY = 0f,
                )
            }
            .width(220.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.elevated.copy(alpha = 0.95f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = {},        // swallow
            ),
    ) {
        actions.forEachIndexed { i, a ->
            if (i > 0) {
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(colors.separator))
            }
            ActionRow(item = a, onClick = { onSelect(a.action) })
        }
    }
}

@Composable
private fun ActionRow(item: BubbleActionItem, onClick: () -> Unit) {
    val colors = KotoTheme.colors
    val fg     = if (item.danger) colors.error else colors.text
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = ripple(bounded = true),
                onClick           = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text       = item.label,
            style      = KotoTheme.typography.bodyMedium,
            color      = fg,
            fontWeight = FontWeight.Medium,
        )
        Icon(
            imageVector        = item.icon,
            contentDescription = null,
            tint               = fg,
            modifier           = Modifier.size(18.dp),
        )
    }
}

// ─── Frozen bubble (non-interactive visual copy) ───────────────────────────

@Composable
private fun FrozenBubble(message: Message) {
    val colors = KotoTheme.colors
    val self   = message.self
    val bg     = if (self) colors.bubbleSelf   else colors.bubblePeer
    val fg     = if (self) colors.onBubbleSelf else colors.onBubblePeer
    val secText = if (self) fg.copy(alpha = 0.78f) else colors.textSecondary
    val shape  = if (self) {
        RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 6.dp, bottomStart = 22.dp)
    } else {
        RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 22.dp, bottomStart = 6.dp)
    }

    Box(
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Column {
            if (!self && message.author != null) {
                Text(
                    text       = message.author,
                    style      = KotoTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color      = message.authorColor ?: colors.accent,
                    modifier   = Modifier.padding(bottom = 2.dp),
                )
            }
            if (message.text.isNotEmpty()) {
                Text(text = message.text, style = KotoTheme.typography.bubble, color = fg)
            }
            Spacer(Modifier.height(2.dp))
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment     = Alignment.CenterVertically,
                modifier              = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text  = message.time,
                    style = KotoTheme.typography.timestamp,
                    color = secText,
                )
            }
        }
    }
}

private val REACTION_EMOJIS = listOf("❤️", "🔥", "😂", "👍", "😮", "🙏")

@androidx.compose.runtime.Immutable
data class ReactionTarget(
    val message : Message,
    val bounds  : BubbleBounds,
)

enum class BubbleAction { Reply, Forward, Copy, Edit, Delete, Pin, Report }

@androidx.compose.runtime.Immutable
private data class BubbleActionItem(
    val action : BubbleAction,
    val label  : String,
    val icon   : androidx.compose.ui.graphics.vector.ImageVector,
    val danger : Boolean,
)
