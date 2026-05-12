package run.koto.desktop.ui.screens.conversation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import run.koto.desktop.ui.components.atoms.EphemeralRing
import run.koto.desktop.ui.components.atoms.MessageRichText
import run.koto.desktop.ui.components.atoms.StatusIcon
import run.koto.desktop.ui.theme.KotoEasing
import run.koto.desktop.ui.theme.KotoTheme

/**
 * Full-featured chat bubble — port of the React mockup's `<Bubble>` in
 * `Conversation.jsx`. Supports author header (for group chats), self/peer
 * coloring, grouping-aware corner radii, timestamps, delivery status, ephemeral
 * ring, reactions chips, and long-press detection.
 *
 * Entrance animation matches CSS `bubblePop` / `bubblePopSelf`:
 *   peer — scale 0.86, translateY +8, alpha 0 → identity   (260 ms KotoEasing)
 *   self — scale 0.90, translateX +8, alpha 0 → identity   (260 ms KotoEasing)
 * Long-press is custom (not `combinedClickable`) so the 400 ms threshold exactly
 * matches the mockup's `pressTimer`.
 */
@Composable
fun MessageBubble(
    grouped       : GroupedMessage,
    modifier      : Modifier = Modifier,
    onLongPress   : (Message, BubbleBounds) -> Unit = { _, _ -> },
    onToggleReact : (Message, String) -> Unit = { _, _ -> },
    onReplyClick  : (String) -> Unit = {},
    hidden        : Boolean = false,
) {
    val msg      = grouped.message
    val colors   = KotoTheme.colors
    val self     = msg.self

    val bg       = if (self) colors.bubbleSelf   else colors.bubblePeer
    val fg       = if (self) colors.onBubbleSelf else colors.onBubblePeer
    val secText  = if (self) fg.copy(alpha = 0.78f) else colors.textSecondary

    val shape    = bubbleShape(self, grouped.endGroup)

    // Entrance animation once per composition key.
    val scaleA      = remember { Animatable(0.86f) }
    val translateXA = remember { Animatable(if (self) 8f else 0f) }
    val translateYA = remember { Animatable(if (self) 0f else 8f) }
    val alphaA      = remember { Animatable(0f) }
    LaunchedEffect(msg.id) {
        val spec = tween<Float>(durationMillis = 260, easing = KotoEasing)
        coroutineScope {
            launch { scaleA     .animateTo(1f, spec) }
            launch { translateXA.animateTo(0f, spec) }
            launch { translateYA.animateTo(0f, spec) }
            launch { alphaA     .animateTo(1f, spec) }
        }
    }

    // Bubble bounds for long-press overlay — must NOT live in Compose state: every
    // onGloballyPositioned during window resize would recompose all visible bubbles.
    val boundsSlot = remember(msg.id) { BubbleBoundsSlot() }

    val scope = rememberCoroutineScope()

    Row(
        modifier             = modifier
            .fillMaxWidth()
            .padding(
                top    = if (grouped.startGroup) 10.dp else 2.dp,
                bottom = if (msg.reactions.isNotEmpty()) 14.dp else 0.dp,
            )
            .graphicsLayer { alpha = if (hidden) 0f else 1f },
        horizontalArrangement = if (self) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .onGloballyPositioned(boundsSlot::updateFrom)
                .graphicsLayer {
                    scaleX          = scaleA.value
                    scaleY          = scaleA.value
                    translationX    = translateXA.value
                    translationY    = translateYA.value
                    alpha           = alphaA.value
                    transformOrigin = TransformOrigin(
                        pivotFractionX = if (self) 1f else 0f,
                        pivotFractionY = 1f,
                    )
                },
        ) {
            BubbleContainer(
                shape            = shape,
                bg               = bg,
                onLongPressStart = {
                    scope.launch {
                        delay(400)
                        onLongPress(msg, boundsSlot.snapshot())
                    }
                },
                onClick          = { /* tap: future — open media viewer for attached images */ },
                content          = {
                    Column {
                        if (!self && msg.author != null && grouped.startGroup) {
                            Text(
                                text       = msg.author,
                                style      = KotoTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color      = msg.authorColor ?: colors.accent,
                                modifier   = Modifier.padding(bottom = 2.dp),
                            )
                        }
                        msg.forwardedFromLabel?.let { fwdLabel ->
                            Text(
                                text       = "Переслано от $fwdLabel",
                                style      = KotoTheme.typography.labelMedium,
                                color      = if (self) Color.White.copy(alpha = 0.85f) else colors.textSecondary,
                                fontWeight = FontWeight.Medium,
                                modifier   = Modifier.padding(bottom = 4.dp),
                            )
                        }
                        msg.replyTo?.let { reply ->
                            ReplyPreviewStrip(
                                preview     = reply,
                                self        = self,
                                onClick     = { onReplyClick(reply.targetId) },
                            )
                            Spacer(Modifier.padding(top = 4.dp))
                        }
                        if (msg.text.isNotEmpty()) {
                            MessageRichText(
                                text      = msg.text,
                                style     = KotoTheme.typography.bubble,
                                color     = fg,
                                linkColor = if (self) Color.White else colors.accent,
                            )
                        }
                        Spacer(Modifier.padding(top = 2.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            modifier              = Modifier.fillMaxWidth(),
                        ) {
                            Spacer(Modifier.weight(1f))
                            if (msg.ephemeral) {
                                EphemeralRing(
                                    pct   = msg.ephemeralPct,
                                    color = secText,
                                    size  = 11.dp,
                                )
                            }
                            if (msg.edited) {
                                Text(
                                    text  = "изменено",
                                    style = KotoTheme.typography.timestamp,
                                    color = secText,
                                )
                            }
                            Text(
                                text  = msg.time,
                                style = KotoTheme.typography.timestamp,
                                color = secText,
                            )
                            if (self && msg.status != null) {
                                StatusIcon(
                                    status = msg.status,
                                    color  = secText,
                                    size   = 14.dp,
                                )
                            }
                        }
                    }
                },
            )

            // Reactions strip — floats below the bubble, offset into the padding we
            // reserved above. Matches the mockup's `absolute bottom: -12` trick.
            if (msg.reactions.isNotEmpty()) {
                Row(
                    modifier              = Modifier
                        .align(if (self) Alignment.BottomStart else Alignment.BottomEnd)
                        .offset(y = 12.dp)
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    msg.reactions.forEach { r ->
                        ReactionChip(
                            reaction = r,
                            onClick  = { onToggleReact(msg, r.emoji) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BubbleContainer(
    shape             : Shape,
    bg                : Color,
    onLongPressStart  : () -> Unit,
    onClick           : () -> Unit,
    content           : @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onLongPressStart()
                    waitForUpOrCancellation()
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        content()
    }
}

@Composable
private fun ReactionChip(
    reaction : Reaction,
    onClick  : () -> Unit,
) {
    val colors = KotoTheme.colors
    val bg     = if (reaction.mine) colors.accent.copy(alpha = 0.2f) else colors.elevated
    val border = if (reaction.mine) colors.accent else colors.separator
    Row(
        modifier              = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(
                width = if (reaction.mine) 1.dp else 0.5.dp,
                color = border,
                shape = RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(text = reaction.emoji, style = KotoTheme.typography.labelLarge, color = colors.text)
        if (reaction.count > 1) {
            Text(
                text       = reaction.count.toString(),
                style      = KotoTheme.typography.labelMedium,
                color      = colors.text,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun bubbleShape(self: Boolean, endGroup: Boolean): Shape {
    // Matches the mockup's radius table exactly:
    //   self   endGroup:true  →  18/4/18/18   (tail corner at bottom-right)
    //   self   endGroup:false →  18/18/4/18   (nose-tucked mid-group)
    //   peer   endGroup:*     →  18/18/18/4   (tail corner at bottom-left)
    val r22 = 22.dp
    val r6  = 6.dp
    return if (self) {
        if (endGroup) RoundedCornerShape(topStart = r22, topEnd = r22, bottomEnd = r6,  bottomStart = r22)
        else          RoundedCornerShape(topStart = r22, topEnd = r22, bottomEnd = r22, bottomStart = r22)
    } else {
        RoundedCornerShape(topStart = r22, topEnd = r22, bottomEnd = r22, bottomStart = r6)
    }
}

/** Latest layout rect without triggering recomposition (resize-safe). */
private class BubbleBoundsSlot {
    private var x = 0f
    private var y = 0f
    private var w = 0f
    private var h = 0f

    fun updateFrom(coords: LayoutCoordinates) {
        val pos = coords.positionInRoot()
        x = pos.x
        y = pos.y
        w = coords.size.width.toFloat()
        h = coords.size.height.toFloat()
    }

    fun snapshot(): BubbleBounds = BubbleBounds(x, y, w, h)
}

/** Screen-space rectangle of a bubble — used by the reaction overlay to anchor
 *  the frozen copy of the bubble at its original position. */
@androidx.compose.runtime.Immutable
data class BubbleBounds(
    val x      : Float,
    val y      : Float,
    val width  : Float,
    val height : Float,
) {
    companion object {
        val ZERO = BubbleBounds(0f, 0f, 0f, 0f)
    }
}

/** Quoted-message strip rendered inside the bubble, above the main text.
 *  Telegram-style: thin accent vertical bar, author name on top, one-line
 *  text snippet below. Click scrolls back to the parent. */
@Composable
private fun ReplyPreviewStrip(
    preview : ReplyPreview,
    self    : Boolean,
    onClick : () -> Unit,
) {
    val colors    = KotoTheme.colors
    val fg        = if (self) colors.onBubbleSelf else colors.onBubblePeer
    val accent    = if (self) Color.White.copy(alpha = 0.85f) else colors.accent
    val secondary = if (self) fg.copy(alpha = 0.78f) else colors.textSecondary
    val bg        = if (self) Color.White.copy(alpha = 0.10f) else colors.background.copy(alpha = 0.55f)

    Row(
        modifier              = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = androidx.compose.material3.ripple(bounded = true),
                onClick           = onClick,
            )
            .padding(start = 0.dp, top = 6.dp, end = 10.dp, bottom = 6.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(start = 6.dp)
                .size(width = 3.dp, height = 28.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent),
        )
        Column {
            Text(
                text       = preview.author,
                style      = KotoTheme.typography.labelMedium,
                color      = accent,
                fontWeight = FontWeight.SemiBold,
                maxLines   = 1,
                overflow   = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                text     = preview.text,
                style    = KotoTheme.typography.bodySmall,
                color    = secondary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
    }
}
