package run.koto.desktop.ui.screens.auth

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import run.koto.desktop.ui.theme.KotoTheme

/**
 * First-run welcome — Koto hero + "Create / Restore" actions.
 *
 * Telegram-minimal layout: logo, title, one-line tagline, primary CTA,
 * secondary text-button. No feature list, no fine print, no disclaimers —
 * users learn the app by using it, not by reading the welcome.
 */
@Composable
fun WelcomeScreen(
    onCreate  : () -> Unit,
    onRestore : () -> Unit,
) {
    val colors = KotoTheme.colors

    Box(modifier = Modifier.fillMaxSize()) {
        AuthBackdrop()

        Column(
            modifier             = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 40.dp),
            horizontalAlignment  = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            KotoLogoTile(size = 128.dp)
            Spacer(Modifier.height(28.dp))
            Text(
                text       = "Koto",
                style      = KotoTheme.typography.displayXL,
                color      = colors.text,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text       = "Приватный мессенджер",
                style      = KotoTheme.typography.titleMedium,
                color      = colors.textSecondary,
                textAlign  = TextAlign.Center,
            )

            Spacer(Modifier.weight(1f))

            Column(
                modifier            = Modifier.width(340.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PrimaryButton(
                    label    = "Создать новый Koto ID",
                    onClick  = onCreate,
                )
                SecondaryButton(
                    label    = "У меня уже есть аккаунт",
                    onClick  = onRestore,
                )
            }
        }
    }
}

@Composable
internal fun PrimaryButton(
    label   : String,
    enabled : Boolean = true,
    onClick : () -> Unit,
) {
    val colors = KotoTheme.colors
    // Enabled = brand orange fill + white text. Disabled = flat surface tint
    // that reads as "not yet" without the muddy 40%-accent-on-black brown.
    val bg    = if (enabled) colors.accent else colors.surface
    val fg    = if (enabled) Color.White   else colors.textSecondary

    Box(
        modifier          = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(
                enabled           = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication        = ripple(bounded = true),
                onClick           = onClick,
            )
            .padding(vertical = 15.dp),
        contentAlignment  = Alignment.Center,
    ) {
        Text(
            text       = label,
            style      = KotoTheme.typography.titleMedium,
            color      = fg,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun SecondaryButton(
    label   : String,
    onClick : () -> Unit,
) {
    val colors = KotoTheme.colors
    Box(
        modifier          = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = ripple(bounded = true),
                onClick           = onClick,
            )
            .padding(vertical = 14.dp),
        contentAlignment  = Alignment.Center,
    ) {
        Text(
            text       = label,
            style      = KotoTheme.typography.titleMedium,
            color      = colors.accent,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
