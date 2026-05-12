package run.koto.desktop.ui.components.atoms

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import run.koto.desktop.ui.theme.KotoEasing
import run.koto.desktop.ui.theme.KotoTheme

@Composable
fun AnimatedIconButton(
    icon         : ImageVector,
    onClick      : () -> Unit,
    modifier     : Modifier = Modifier,
    size         : Dp       = 36.dp,
    iconSize     : Dp       = 20.dp,
    idleTint     : Color    = KotoTheme.colors.textSecondary,
    hoverTint    : Color    = KotoTheme.colors.accent,
    enabled      : Boolean  = true,
    contentDescription: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()

    val tint by animateColorAsState(
        targetValue = when {
            !enabled -> idleTint.copy(alpha = 0.4f)
            hovered  -> hoverTint
            else     -> idleTint
        },
        animationSpec = tween(160, easing = KotoEasing),
        label         = "tint",
    )
    val scale by animateFloatAsState(
        targetValue   = when {
            pressed -> 0.92f
            hovered -> 1.08f
            else    -> 1f
        },
        animationSpec = tween(160, easing = KotoEasing),
        label         = "scale",
    )
    val bgAlpha by animateFloatAsState(
        targetValue   = if (hovered) 1f else 0f,
        animationSpec = tween(140, easing = KotoEasing),
        label         = "bg",
    )

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(KotoTheme.colors.surface.copy(alpha = 0.55f * bgAlpha))
            .hoverable(interaction, enabled)
            .clickable(
                enabled           = enabled,
                interactionSource = interaction,
                indication        = null,
                onClick           = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = contentDescription,
            tint               = tint,
            modifier           = Modifier
                .size(iconSize)
                .graphicsLayer { scaleX = scale; scaleY = scale },
        )
    }
}

@Composable
fun AnimatedIconButton(
    painter      : Painter,
    onClick      : () -> Unit,
    modifier     : Modifier = Modifier,
    size         : Dp       = 36.dp,
    iconSize     : Dp       = 20.dp,
    idleTint     : Color    = KotoTheme.colors.textSecondary,
    hoverTint    : Color    = KotoTheme.colors.accent,
    enabled      : Boolean  = true,
    contentDescription: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()

    val tint by animateColorAsState(
        targetValue = when {
            !enabled -> idleTint.copy(alpha = 0.4f)
            hovered  -> hoverTint
            else     -> idleTint
        },
        animationSpec = tween(160, easing = KotoEasing),
        label         = "tint",
    )
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.92f
            hovered -> 1.08f
            else    -> 1f
        },
        animationSpec = tween(160, easing = KotoEasing),
        label         = "scale",
    )
    val bgAlpha by animateFloatAsState(
        targetValue = if (hovered) 1f else 0f,
        animationSpec = tween(140, easing = KotoEasing),
        label         = "bg",
    )

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(KotoTheme.colors.surface.copy(alpha = 0.55f * bgAlpha))
            .hoverable(interaction, enabled)
            .clickable(
                enabled           = enabled,
                interactionSource = interaction,
                indication        = null,
                onClick           = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter            = painter,
            contentDescription = contentDescription,
            tint               = tint,
            modifier           = Modifier
                .size(iconSize)
                .graphicsLayer { scaleX = scale; scaleY = scale },
        )
    }
}
