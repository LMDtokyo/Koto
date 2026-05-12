package run.koto.desktop.ui.screens.overlays

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import run.koto.desktop.ui.icons.KotoIcons
import run.koto.desktop.ui.theme.KotoEasing
import run.koto.desktop.ui.theme.KotoTheme

/**
 * Ephemeral (disappearing-message) timer picker. Opens from the conversation
 * header ⋮ menu or a dedicated timer icon. Presets match Signal's defaults:
 * off / 30s / 5m / 1h / 8h / 1d / 1w / custom.
 */
@Composable
fun EphemeralSheet(
    currentSeconds : Long,
    onDismiss      : () -> Unit,
    onPick         : (Long) -> Unit,
) {
    val colors = KotoTheme.colors
    var selected by remember { mutableStateOf(currentSeconds) }

    val scrimAlpha = remember { Animatable(0f) }
    val slideY     = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        coroutineScope {
            launch { scrimAlpha.animateTo(1f, tween(180)) }
            launch { slideY    .animateTo(0f, tween(320, easing = KotoEasing)) }
        }
    }

    val presets = listOf(
        0L            to "Выключено",
        30L           to "30 секунд",
        300L          to "5 минут",
        3600L         to "1 час",
        28800L        to "8 часов",
        86400L        to "1 день",
        604800L       to "1 неделя",
    )

    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f * scrimAlpha.value))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = onDismiss,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationY = slideY.value * 800f }
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(colors.background)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = null,
                    onClick           = {},
                )
                .padding(top = 8.dp, bottom = 24.dp, start = 16.dp, end = 16.dp),
        ) {
            Box(
                modifier = Modifier
                    .padding(bottom = 14.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.separator)
                    .align(Alignment.CenterHorizontally),
            )

            Row(
                modifier              = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector        = KotoIcons.Timer,
                    contentDescription = null,
                    tint               = colors.accent,
                    modifier           = Modifier.size(20.dp),
                )
                Column {
                    Text(
                        text       = "Исчезающие сообщения",
                        style      = KotoTheme.typography.titleMedium,
                        color      = colors.text,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text  = "Сообщения будут удалены у всех участников по истечении времени.",
                        style = KotoTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surface),
            ) {
                presets.forEachIndexed { i, (sec, label) ->
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication        = ripple(bounded = true),
                                onClick           = { selected = sec },
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(text = label, style = KotoTheme.typography.bodyLarge, color = colors.text)
                        if (selected == sec) {
                            Icon(
                                imageVector        = KotoIcons.Check,
                                contentDescription = null,
                                tint               = colors.accent,
                                modifier           = Modifier.size(18.dp),
                            )
                        }
                    }
                    if (i < presets.lastIndex) {
                        Box(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(0.5.dp).background(colors.separator))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Box(
                modifier         = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.accent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication        = ripple(bounded = true),
                        onClick           = { onPick(selected); onDismiss() },
                    )
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text       = if (selected == 0L) "Выключить" else "Применить",
                    style      = KotoTheme.typography.titleSmall,
                    color      = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
