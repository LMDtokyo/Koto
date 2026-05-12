package run.koto.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import run.koto.desktop.ui.theme.KotoTheme

/**
 * Temporary placeholder screen. Used during incremental UI build-out — every nav
 * destination initially shows its title + a short description of what's coming, plus a
 * "next" button so the navigation stack can be exercised before real screens land.
 *
 * All placeholders will be replaced by faithful ports of the React mockup screens in
 * phase 2 / 3. This file should be deleted once there are no usages left.
 */
@Composable
fun PlaceholderScreen(
    title       : String,
    description : String,
    onNext      : () -> Unit,
) {
    Box(
        modifier        = Modifier.fillMaxSize().background(KotoTheme.colors.background).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(KotoTheme.shapes.logoTile)
                    .background(KotoTheme.colors.accentGradient)
                    .padding(horizontal = 22.dp, vertical = 22.dp),
            ) {
                Text(
                    text  = "Koto",
                    style = KotoTheme.typography.headlineMedium,
                    color = KotoTheme.colors.onAccent,
                )
            }
            Text(
                text  = title,
                style = KotoTheme.typography.displayMedium,
                color = KotoTheme.colors.text,
            )
            Text(
                text  = description,
                style = KotoTheme.typography.bodyMedium,
                color = KotoTheme.colors.textSecondary,
            )
            Button(
                onClick  = onNext,
                colors   = ButtonDefaults.buttonColors(containerColor = KotoTheme.colors.accent),
                shape    = RoundedCornerShape(14.dp),
            ) {
                Text("Next", color = KotoTheme.colors.onAccent, style = KotoTheme.typography.titleMedium)
            }
        }
    }
}
