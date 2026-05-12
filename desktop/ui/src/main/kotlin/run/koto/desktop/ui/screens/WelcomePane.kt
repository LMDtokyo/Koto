package run.koto.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import run.koto.desktop.ui.theme.KotoTheme

/**
 * "Nothing selected" state for the main pane — the Telegram Desktop equivalent of the
 * centred wallpaper/logo users see before they pick a conversation. Keeps the screen
 * from looking empty-abandoned and sets visual tone (brand tile, reassuring copy about
 * encryption).
 */
@Composable
fun WelcomePane() {
    val colors = KotoTheme.colors
    val type   = KotoTheme.typography

    Box(
        modifier         = Modifier.fillMaxSize().background(colors.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier            = Modifier.widthIn(max = 400.dp).padding(horizontal = 32.dp),
        ) {
            // Brand tile — gradient-filled squircle with the cat silhouette placeholder.
            // Replace with the actual koto-cat.png asset once we bundle it into :ui/resources.
            Box(
                modifier        = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(colors.accentGradient),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text  = "Koto",
                    style = type.displayLarge,
                    color = colors.onAccent,
                )
            }
            Spacer(Modifier.height(28.dp))
            Text(
                text      = "Выберите чат, чтобы начать",
                style     = type.headlineSmall,
                color     = colors.text,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text      = "Сквозное шифрование. Никаких телефонов, никакой телеметрии. " +
                            "Выберите существующий диалог слева или начните новый.",
                style     = type.bodyMedium,
                color     = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
