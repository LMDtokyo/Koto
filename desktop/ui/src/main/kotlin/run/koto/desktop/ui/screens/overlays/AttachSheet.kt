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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import run.koto.desktop.ui.icons.KotoIcons
import run.koto.desktop.ui.theme.KotoEasing
import run.koto.desktop.ui.theme.KotoTheme

/**
 * Attach-menu bottom sheet — opens when the composer "+" is tapped. Grid of
 * colored tiles: Camera, Photo, File, Location, Contact, Voice, Poll, Event.
 *
 * Entrance: scrim fades in 180 ms, sheet slides up 320 ms with KotoEasing.
 * Dismiss on scrim tap or Esc (ESC handled by caller via focus listener).
 */
@Composable
fun AttachSheet(
    onDismiss      : () -> Unit,
    onPickCamera   : () -> Unit = {},
    onPickPhoto    : () -> Unit = {},
    onPickFile     : () -> Unit = {},
    onPickLocation : () -> Unit = {},
    onPickContact  : () -> Unit = {},
    onPickVoice    : () -> Unit = {},
) {
    val colors = KotoTheme.colors

    val scrimAlpha = remember { Animatable(0f) }
    val slideY     = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.coroutineScope {
            launch { scrimAlpha.animateTo(1f, tween(180)) }
            launch { slideY    .animateTo(0f, tween(320, easing = KotoEasing)) }
        }
    }

    Box(
        modifier = Modifier
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
                    onClick           = {},      // swallow taps in sheet
                )
                .padding(top = 8.dp, bottom = 32.dp, start = 20.dp, end = 20.dp),
        ) {
            // Drag handle
            Box(
                modifier         = Modifier
                    .padding(bottom = 12.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.separator)
                    .align(Alignment.CenterHorizontally),
            )
            Text(
                text       = "Прикрепить",
                style      = KotoTheme.typography.titleLarge,
                color      = colors.text,
                fontWeight = FontWeight.Bold,
                modifier   = Modifier.padding(bottom = 14.dp, start = 6.dp),
            )

            val tiles = listOf(
                AttachTileData("Камера",       KotoIcons.Camera,   Color(0xFFFF6B35), onPickCamera),
                AttachTileData("Фото/Видео",   KotoIcons.Image,    Color(0xFF7C5CFF), onPickPhoto),
                AttachTileData("Файл",         KotoIcons.Document, Color(0xFF3276FF), onPickFile),
                AttachTileData("Геолокация",   KotoIcons.Location, Color(0xFF00A676), onPickLocation),
                AttachTileData("Контакт",      KotoIcons.Contact,  Color(0xFFF0B400), onPickContact),
                AttachTileData("Голос",        KotoIcons.Mic,      Color(0xFFE74C6F), onPickVoice),
                AttachTileData("Опрос",        KotoIcons.Poll,     Color(0xFF13B3A5), {}),
                AttachTileData("Подарок",      KotoIcons.Gift,     Color(0xFFFF7A9A), {}),
            )

            // 4 × 2 tile grid
            tiles.chunked(4).forEachIndexed { rowIdx, row ->
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { tile ->
                        AttachTile(
                            data     = tile,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Pad the last row if partial
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private data class AttachTileData(
    val label   : String,
    val icon    : androidx.compose.ui.graphics.vector.ImageVector,
    val color   : Color,
    val onClick : () -> Unit,
)

@Composable
private fun AttachTile(data: AttachTileData, modifier: Modifier = Modifier) {
    Column(
        modifier            = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = ripple(bounded = true),
                onClick           = data.onClick,
            )
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier          = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(data.color.copy(alpha = 0.14f)),
            contentAlignment  = Alignment.Center,
        ) {
            Icon(
                imageVector        = data.icon,
                contentDescription = null,
                tint               = data.color,
                modifier           = Modifier.size(26.dp),
            )
        }
        Text(
            text      = data.label,
            style     = KotoTheme.typography.labelMedium,
            color     = KotoTheme.colors.text,
            textAlign = TextAlign.Center,
        )
    }
}
