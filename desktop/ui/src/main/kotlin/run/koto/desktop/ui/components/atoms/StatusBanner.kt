package run.koto.desktop.ui.components.atoms

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import run.koto.desktop.ui.icons.KotoIcons
import run.koto.desktop.ui.theme.KotoTheme

enum class BannerKind { Offline, Connecting, Error, Info }

@Composable
fun StatusBanner(
    visible : Boolean,
    text    : String,
    kind    : BannerKind = BannerKind.Offline,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible  = visible,
        enter    = slideInVertically  { -it } + fadeIn(),
        exit     = slideOutVertically { -it } + fadeOut(),
        modifier = modifier,
    ) {
        val (bg, fg, icon) = when (kind) {
            BannerKind.Offline    -> Triple(KotoTheme.colors.warning,        Color.Black,                      KotoIcons.Globe)
            BannerKind.Connecting -> Triple(KotoTheme.colors.info,           Color.White,                      KotoIcons.Globe)
            BannerKind.Error      -> Triple(KotoTheme.colors.error,          Color.White,                      KotoIcons.Alert)
            BannerKind.Info       -> Triple(KotoTheme.colors.surface,        KotoTheme.colors.text,            KotoIcons.DotCircle)
        } as Triple<Color, Color, ImageVector>

        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .background(bg)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = fg,
                modifier           = Modifier.size(14.dp),
            )
            Text(
                text       = text,
                style      = KotoTheme.typography.labelLarge,
                color      = fg,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
