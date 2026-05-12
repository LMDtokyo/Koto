package run.koto.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import run.koto.network.ConnectivityManager
import run.koto.network.ServerStatus
import run.koto.ui.theme.KotoTheme

/**
 * Slim status banner pinned to the top of the screen that fades in when
 * the server is unreachable and fades out when it's healthy again.
 *
 * Pass the [ConnectivityManager] directly from the hosting screen — it's
 * a singleton, so re-use across composables is free.
 */
@Composable
fun ConnectivityBanner(connectivityManager: ConnectivityManager) {
    val status by connectivityManager.status.collectAsStateWithLifecycle()
    val colors = KotoTheme.colors

    val visible = status == ServerStatus.DISCONNECTED || status == ServerStatus.CHECKING

    AnimatedVisibility(
        visible = visible,
        enter   = expandVertically(spring(stiffness = Spring.StiffnessMedium)) +
                  fadeIn(spring(stiffness = Spring.StiffnessMedium)),
        exit    = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) +
                  fadeOut(spring(stiffness = Spring.StiffnessMediumLow)),
    ) {
        val bg = if (status == ServerStatus.CHECKING) colors.primary.copy(alpha = 0.90f)
                 else colors.error.copy(alpha = 0.95f)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(bg)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                // Pulsing dot (error) or spinner (checking)
                if (status == ServerStatus.CHECKING) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(12.dp),
                        color       = colors.onPrimary,
                        strokeWidth = 1.5.dp,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(colors.onPrimary),
                    )
                }
                Text(
                    text     = when (status) {
                        ServerStatus.CHECKING      -> "Проверка соединения…"
                        ServerStatus.DISCONNECTED  -> "Нет соединения с сервером"
                        else                        -> ""
                    },
                    color    = colors.onPrimary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
