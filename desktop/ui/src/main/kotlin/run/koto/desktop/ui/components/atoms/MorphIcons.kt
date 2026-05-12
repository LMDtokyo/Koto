package run.koto.desktop.ui.components.atoms

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import run.koto.desktop.ui.icons.KotoIcons
import run.koto.desktop.ui.theme.KotoEasing
import run.koto.desktop.ui.theme.KotoTheme

@Composable
fun PlusMorphIcon(
    expanded     : Boolean,
    onClick      : () -> Unit,
    modifier     : Modifier = Modifier,
    size         : Dp       = 36.dp,
    idleTint     : Color    = KotoTheme.colors.textSecondary,
    hoverTint    : Color    = KotoTheme.colors.accent,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()

    val rotation by animateFloatAsState(
        targetValue   = if (expanded) 135f else 0f,
        animationSpec = tween(260, easing = KotoEasing),
        label         = "rot",
    )
    val tint by animateColorAsState(
        targetValue   = when {
            expanded -> KotoTheme.colors.accent
            hovered  -> hoverTint
            else     -> idleTint
        },
        animationSpec = tween(160, easing = KotoEasing),
        label         = "tint",
    )
    val scale by animateFloatAsState(
        targetValue   = when {
            pressed -> 0.9f
            hovered -> 1.08f
            else    -> 1f
        },
        animationSpec = tween(160, easing = KotoEasing),
        label         = "scale",
    )

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(KotoTheme.colors.surface.copy(alpha = if (hovered) 0.55f else 0f))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector        = KotoIcons.Plus,
            contentDescription = null,
            tint               = tint,
            modifier           = Modifier
                .size(size * 0.55f)
                .graphicsLayer { rotationZ = rotation; scaleX = scale; scaleY = scale },
        )
    }
}

@Composable
fun SendMicMorphIcon(
    hasText  : Boolean,
    onSend   : () -> Unit,
    onRecord : () -> Unit,
    modifier : Modifier = Modifier,
    size     : Dp       = 36.dp,
) {
    val accent = KotoTheme.colors.accent
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()

    val bg by animateColorAsState(
        targetValue   = if (hasText) accent else Color.Transparent,
        animationSpec = tween(220, easing = KotoEasing),
        label         = "bg",
    )
    val containerScale by animateFloatAsState(
        targetValue   = when {
            pressed         -> 0.88f
            hovered         -> 1.08f
            hasText         -> 1f
            else            -> 0.92f
        },
        animationSpec = tween(220, easing = KotoEasing),
        label         = "c-scale",
    )
    val micAlpha  = if (hasText) 0f else 1f
    val sendAlpha = if (hasText) 1f else 0f

    val micRot by animateFloatAsState(
        targetValue   = if (hasText) -20f else 0f,
        animationSpec = tween(200, easing = KotoEasing),
        label         = "mic-rot",
    )
    val sendRot by animateFloatAsState(
        targetValue   = if (hasText) 0f else 20f,
        animationSpec = tween(200, easing = KotoEasing),
        label         = "send-rot",
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { scaleX = containerScale; scaleY = containerScale }
            .clip(CircleShape)
            .background(bg)
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication        = null,
                onClick           = { if (hasText) onSend() else onRecord() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector        = KotoIcons.Mic,
            contentDescription = null,
            tint               = KotoTheme.colors.accent,
            modifier           = Modifier
                .size(size * 0.6f)
                .graphicsLayer { alpha = micAlpha; rotationZ = micRot },
        )
        Icon(
            imageVector        = KotoIcons.Send,
            contentDescription = null,
            tint               = KotoTheme.colors.onAccent,
            modifier           = Modifier
                .size(size * 0.5f)
                .graphicsLayer { alpha = sendAlpha; rotationZ = sendRot },
        )
    }
}

@Composable
fun BellShakeIcon(
    shakeTrigger : Int,
    onClick      : () -> Unit,
    modifier     : Modifier = Modifier,
    size         : Dp       = 36.dp,
) {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(shakeTrigger) {
        if (shakeTrigger > 0) {
            shake.animateTo(
                targetValue   = 0f,
                animationSpec = keyframes {
                    durationMillis = 480
                    0f   at 0
                    -12f at 60
                    12f  at 120
                    -8f  at 200
                    6f   at 280
                    -3f  at 360
                    0f   at 480
                },
            )
        }
    }
    AnimatedIconButton(
        icon     = KotoIcons.Bell,
        onClick  = onClick,
        modifier = modifier.graphicsLayer { rotationZ = shake.value },
        size     = size,
    )
}

@Composable
fun MicRecordingIcon(
    recording : Boolean,
    onClick   : () -> Unit,
    modifier  : Modifier = Modifier,
    size      : Dp       = 36.dp,
) {
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(recording) {
        if (recording) {
            pulse.animateTo(
                targetValue   = 1.12f,
                animationSpec = infiniteRepeatable(
                    animation  = tween(560, easing = KotoEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            )
        } else pulse.snapTo(1f)
    }
    AnimatedIconButton(
        icon      = KotoIcons.Mic,
        onClick   = onClick,
        modifier  = modifier.graphicsLayer { scaleX = pulse.value; scaleY = pulse.value },
        size      = size,
        idleTint  = if (recording) KotoTheme.colors.accent else KotoTheme.colors.textSecondary,
        hoverTint = KotoTheme.colors.accent,
    )
}
