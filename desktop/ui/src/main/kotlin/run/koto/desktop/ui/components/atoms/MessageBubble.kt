package run.koto.desktop.ui.components.atoms

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import run.koto.desktop.ui.theme.KotoEasing
import run.koto.desktop.ui.theme.KotoTheme

/**
 * Chat bubble — self (outgoing) or peer (incoming) variant with the mockup's
 * asymmetric tail. An optional reactions strip sits below.
 *
 * Entrance animation matches CSS `bubblePop` / `bubblePopSelf`:
 *   scale(0.86) translateY(+8px)  →  scale(1) translateY(0)    // peer
 *   scale(0.9)  translateX(+8px)  →  scale(1) translateX(0)    // self
 * Duration 260 ms, Koto signature easing. Driven by a [Animatable] on the composition
 * key, so re-rendering the same bubble does NOT retrigger the animation.
 */
@Composable
fun MessageBubble(
    text         : String,
    timestamp    : String,
    isSelf       : Boolean,
    modifier     : Modifier        = Modifier,
    status       : MessageStatus?  = null,
    reactions    : List<String>    = emptyList(),
    ephemeralPct : Float?          = null,
    showTail     : Boolean         = true,
) {
    val colors = KotoTheme.colors
    val shape  = bubbleShape(isSelf, showTail)

    // bubblePop entrance: scale 0.86 → 1, translate 8 px → 0, alpha 0 → 1 over 260 ms.
    val scaleA      = remember { Animatable(0.86f) }
    val translateYA = remember { Animatable(if (isSelf) 0f else 8f) }
    val translateXA = remember { Animatable(if (isSelf) 8f else 0f) }
    val alphaA      = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        val spec = tween<Float>(durationMillis = 260, easing = KotoEasing)
        coroutineScope {
            launch { scaleA     .animateTo(1f, spec) }
            launch { translateYA.animateTo(0f, spec) }
            launch { translateXA.animateTo(0f, spec) }
            launch { alphaA     .animateTo(1f, spec) }
        }
    }

    val bg    = if (isSelf) colors.bubbleSelf else colors.bubblePeer
    val fg    = if (isSelf) colors.onBubbleSelf else colors.onBubblePeer
    val statusColor = if (isSelf) {
        if (status == MessageStatus.READ) colors.onBubbleSelf
        else colors.onBubbleSelf.copy(alpha = 0.75f)
    } else colors.textSecondary

    Column(
        modifier             = modifier.fillMaxWidth(),
        horizontalAlignment  = if (isSelf) Alignment.End else Alignment.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .graphicsLayer {
                    scaleX          = scaleA.value
                    scaleY          = scaleA.value
                    translationX    = translateXA.value
                    translationY    = translateYA.value
                    alpha           = alphaA.value
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                        pivotFractionX = if (isSelf) 1f else 0f,
                        pivotFractionY = 1f,
                    )
                }
                .clip(shape)
                .background(bg)
                .padding(
                    horizontal = KotoTheme.spacing.bubblePaddingH,
                    vertical   = KotoTheme.spacing.bubblePaddingV,
                ),
        ) {
            Column {
                Text(
                    text  = text,
                    style = KotoTheme.typography.bubble,
                    color = fg,
                )
                Spacer(Modifier.padding(top = 2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    modifier              = Modifier.fillMaxWidth(),
                ) {
                    if (ephemeralPct != null) {
                        EphemeralRing(pct = ephemeralPct, color = fg.copy(alpha = 0.6f), size = 11.dp)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text  = timestamp,
                        style = KotoTheme.typography.timestamp,
                        color = fg.copy(alpha = 0.62f),
                    )
                    if (status != null && isSelf) {
                        StatusIcon(status = status, color = statusColor, size = 14.dp)
                    }
                }
            }
        }

        if (reactions.isNotEmpty()) {
            Spacer(Modifier.padding(top = 4.dp))
            Row(
                modifier             = Modifier.padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                reactions.forEach { emoji ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.elevated)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(text = emoji, style = KotoTheme.typography.labelSmall, color = colors.text)
                    }
                }
            }
        }
    }
}

@Composable
private fun bubbleShape(isSelf: Boolean, showTail: Boolean): Shape {
    if (!showTail) return KotoTheme.shapes.bubbleFlat
    return if (isSelf) KotoTheme.shapes.bubbleSelf else KotoTheme.shapes.bubblePeer
}

