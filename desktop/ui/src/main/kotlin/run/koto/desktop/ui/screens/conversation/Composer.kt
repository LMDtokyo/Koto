package run.koto.desktop.ui.screens.conversation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ripple
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import run.koto.desktop.ui.icons.KotoIcons
import run.koto.desktop.ui.theme.KotoTheme

data class ComposerContext(
    val mode         : Mode,
    val authorOrSelf : String,
    val text         : String,
    /** Server id of the message this context refers to. For Reply, that's
     *  the message being quoted; for Edit, the message being edited. */
    val messageId    : String,
) {
    enum class Mode { Reply, Edit }
}

@Composable
fun Composer(
    draft        : String,
    placeholder  : String,
    onDraftChange: (String) -> Unit,
    onSend       : () -> Unit,
    onOpenAttach : () -> Unit,
    onOpenEmoji  : () -> Unit,
    modifier     : Modifier = Modifier,
    context      : ComposerContext? = null,
    onCancelContext : () -> Unit = {},
) {
    val colors  = KotoTheme.colors
    val hasText = draft.trim().isNotEmpty()

    val fieldBorder by animateColorAsState(
        targetValue   = if (hasText) colors.accent.copy(alpha = 0.35f) else androidx.compose.ui.graphics.Color.Transparent,
        animationSpec = tween(220),
        label         = "composer-border",
    )
    val sendBg by animateColorAsState(
        targetValue   = if (hasText) colors.accent else androidx.compose.ui.graphics.Color.Transparent,
        animationSpec = tween(220),
        label         = "send-bg",
    )
    val sendScale by animateFloatAsState(
        targetValue   = if (hasText) 1f else 0.92f,
        animationSpec = tween(240),
        label         = "send-scale",
    )

    Box(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (context != null) {
                ComposerContextStrip(
                    context = context,
                    onCancel = onCancelContext,
                )
            }
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // ── Attach (+) ──────────────────────────────────────────────
                CircleIconButton(
                    icon    = KotoIcons.Plus,
                    tint    = colors.accent,
                    bg      = colors.surface,
                    onClick = onOpenAttach,
                )

                // ── Input field ─────────────────────────────────────────────
                Row(
                    modifier              = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.surface)
                        .border(1.dp, fieldBorder, RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .heightIn(min = 36.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (draft.isEmpty()) {
                            Text(
                                text  = placeholder,
                                style = KotoTheme.typography.bodyMedium,
                                color = colors.textTertiary,
                            )
                        }
                        BasicTextField(
                            value                = draft,
                            onValueChange        = onDraftChange,
                            singleLine           = false,
                            maxLines             = 6,
                            textStyle            = LocalTextStyle.current.merge(
                                KotoTheme.typography.bodyMedium.copy(color = colors.text),
                            ),
                            cursorBrush          = SolidColor(colors.accent),
                            keyboardOptions      = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions      = KeyboardActions(onSend = { onSend() }),
                            modifier             = Modifier
                                .fillMaxWidth()
                                .onPreviewKeyEvent { e ->
                                    // Enter-to-send; Shift+Enter inserts newline (default).
                                    if (e.type == KeyEventType.KeyDown && e.key == Key.Enter && !e.isShiftPressed) {
                                        onSend()
                                        true
                                    } else false
                                },
                        )
                    }
                    SmallIconButton(
                        icon    = KotoIcons.Sticker,
                        tint    = colors.textSecondary,
                        onClick = onOpenEmoji,
                    )
                }

                // ── Send / Mic morph ────────────────────────────────────────
                Box(
                    modifier          = Modifier
                        .size(36.dp)
                        .graphicsLayer { scaleX = sendScale; scaleY = sendScale }
                        .clip(CircleShape)
                        .background(sendBg)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication        = ripple(bounded = true, radius = 18.dp),
                            onClick           = {
                                if (hasText) onSend() else { /* mic: push-to-talk — Phase 3.1 */ }
                            },
                        ),
                    contentAlignment  = Alignment.Center,
                ) {
                    // Mic glyph — visible only when there's NO text. Rotates out as send swoops in.
                    Box(
                        modifier         = Modifier.graphicsLayer {
                            alpha     = if (hasText) 0f else 1f
                            scaleX    = if (hasText) 0.5f else 1f
                            scaleY    = if (hasText) 0.5f else 1f
                            rotationZ = if (hasText) -20f else 0f
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector        = KotoIcons.Mic,
                            contentDescription = null,
                            tint               = colors.accent,
                            modifier           = Modifier.size(22.dp),
                        )
                    }
                    // Send glyph — visible only when there IS text.
                    Box(
                        modifier         = Modifier.graphicsLayer {
                            alpha     = if (hasText) 1f else 0f
                            scaleX    = if (hasText) 1f else 0.5f
                            scaleY    = if (hasText) 1f else 0.5f
                            rotationZ = if (hasText) 0f else 20f
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector        = KotoIcons.Send,
                            contentDescription = null,
                            tint               = colors.onAccent,
                            modifier           = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun ComposerContextStrip(
    context  : ComposerContext,
    onCancel : () -> Unit,
) {
    val colors = KotoTheme.colors
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .heightIn(min = 28.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.accent),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = if (context.mode == ComposerContext.Mode.Reply)
                    "Ответ ${context.authorOrSelf}"
                    else "Редактирование",
                style      = KotoTheme.typography.labelMedium,
                color      = colors.accent,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            )
            Text(
                text     = context.text,
                style    = KotoTheme.typography.bodySmall,
                color    = colors.textSecondary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = ripple(bounded = true, radius = 14.dp),
                    onClick           = onCancel,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = KotoIcons.Close,
                contentDescription = "Отменить",
                tint               = colors.textSecondary,
                modifier           = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun CircleIconButton(
    icon    : androidx.compose.ui.graphics.vector.ImageVector,
    tint    : androidx.compose.ui.graphics.Color,
    bg      : androidx.compose.ui.graphics.Color,
    onClick : () -> Unit,
) {
    Box(
        modifier          = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = ripple(bounded = true, radius = 18.dp),
                onClick           = onClick,
            ),
        contentAlignment  = Alignment.Center,
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = tint,
            modifier           = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun SmallIconButton(
    icon    : androidx.compose.ui.graphics.vector.ImageVector,
    tint    : androidx.compose.ui.graphics.Color,
    onClick : () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = ripple(bounded = false, radius = 14.dp),
                onClick           = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = tint,
            modifier           = Modifier.size(20.dp),
        )
    }
}
