package run.koto.desktop.ui.screens.others

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import run.koto.desktop.ui.icons.KotoIcons
import run.koto.desktop.ui.theme.KotoTheme

/**
 * Safety number verification screen — sketch of the Signal-style flow. Real
 * SAS / safety-number computation happens in the Rust crypto crate; this is
 * a visual scaffold that displays a matrix of 6×10 digit groups + QR preview.
 */
@Composable
fun SafetyScreen(peerName: String, onBack: () -> Unit) {
    val colors = KotoTheme.colors

    // Mock safety number — 60 digits in 6 groups of 10 chars, space-separated
    val safetyNumber = remember {
        val r = kotlin.random.Random(peerName.hashCode())
        List(6) { (1..10).joinToString("") { r.nextInt(10).toString() } }
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // Top bar
            Row(
                modifier              = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Row(
                    modifier              = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication        = ripple(bounded = false, radius = 60.dp),
                            onClick           = onBack,
                        )
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        imageVector        = KotoIcons.Back,
                        contentDescription = null,
                        tint               = colors.accent,
                        modifier           = Modifier.size(22.dp),
                    )
                    Text(text = "Назад", style = KotoTheme.typography.titleMedium, color = colors.accent)
                }
                Text(
                    text       = "Проверка",
                    style      = KotoTheme.typography.titleLarge,
                    color      = colors.text,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier.weight(1f),
                    textAlign  = TextAlign.Center,
                )
                Spacer(Modifier.size(80.dp))
            }

            // Intro
            Column(
                modifier             = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 8.dp),
                horizontalAlignment  = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier          = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.accent.copy(alpha = 0.15f)),
                    contentAlignment  = Alignment.Center,
                ) {
                    Icon(
                        imageVector        = KotoIcons.Shield,
                        contentDescription = null,
                        tint               = colors.accent,
                        modifier           = Modifier.size(32.dp),
                    )
                }
                Spacer(Modifier.size(14.dp))
                Text(
                    text       = "Номер безопасности",
                    style      = KotoTheme.typography.headlineMedium,
                    color      = colors.text,
                    fontWeight = FontWeight.Bold,
                    textAlign  = TextAlign.Center,
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text      = "Сравните этот номер с $peerName, чтобы убедиться, что вас никто не подслушивает. Совпадает у обоих — значит шифрование честное.",
                    style     = KotoTheme.typography.bodyMedium,
                    color     = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }

            // QR placeholder
            Box(
                modifier         = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    // Fake QR pattern with icon
                    Icon(
                        imageVector        = KotoIcons.Qr,
                        contentDescription = null,
                        tint               = Color.Black,
                        modifier           = Modifier.size(160.dp),
                    )
                }
            }

            // Safety number matrix
            Column(
                modifier            = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surface)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text       = "НОМЕР БЕЗОПАСНОСТИ",
                    style      = KotoTheme.typography.labelMedium,
                    color      = colors.textSecondary,
                    fontWeight = FontWeight.SemiBold,
                )
                // 6 rows, each 2 groups of 10 chars — readable chunking
                safetyNumber.chunked(2).forEach { pair ->
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        pair.forEach { group ->
                            Text(
                                text       = group,
                                style      = KotoTheme.typography.monoLarge,
                                color      = colors.text,
                                fontWeight = FontWeight.Medium,
                                modifier   = Modifier.weight(1f),
                                textAlign  = TextAlign.Center,
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            Spacer(Modifier.size(16.dp))

            // Verify button
            Box(
                modifier         = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.accent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication        = ripple(bounded = true),
                            onClick           = { /* mark verified — Phase 4 */ },
                        )
                        .padding(vertical = 14.dp),
                    contentAlignment  = Alignment.Center,
                ) {
                    Text(
                        text       = "Подтвердить совпадение",
                        style      = KotoTheme.typography.titleMedium,
                        color      = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(Modifier.size(40.dp))
        }
    }
}
