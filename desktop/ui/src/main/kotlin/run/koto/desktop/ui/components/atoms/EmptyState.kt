package run.koto.desktop.ui.components.atoms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import run.koto.desktop.ui.theme.KotoTheme

@Composable
fun EmptyState(
    icon     : ImageVector,
    title    : String,
    message  : String,
    modifier : Modifier = Modifier,
) {
    val colors = KotoTheme.colors
    Box(modifier = modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = colors.textTertiary,
                modifier           = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text       = title,
                style      = KotoTheme.typography.titleLarge,
                color      = colors.text,
                fontWeight = FontWeight.SemiBold,
                textAlign  = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text      = message,
                style     = KotoTheme.typography.bodyMedium,
                color     = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
