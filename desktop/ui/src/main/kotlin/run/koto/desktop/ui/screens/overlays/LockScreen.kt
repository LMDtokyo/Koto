package run.koto.desktop.ui.screens.overlays

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import run.koto.desktop.ui.icons.KotoIcons
import run.koto.desktop.ui.screens.auth.AuthBackdrop
import run.koto.desktop.ui.screens.auth.KotoLogoTile
import run.koto.desktop.ui.theme.KotoEasing
import run.koto.desktop.ui.theme.KotoTheme

/**
 * Screen lock — shown when the OS-keychain-backed lock policy fires (idle
 * timeout, suspend/resume, user-triggered). Enters a 6-digit PIN; wrong entry
 * shakes the dot row. Matches the mockup's gated "unlock" pattern.
 *
 * Biometric support lives behind this screen on Android/iOS; desktop falls
 * back to PIN-only until the OS SDKs land (Phase 4).
 */
@Composable
fun LockScreen(
    onUnlock  : () -> Unit,
) {
    val colors = KotoTheme.colors
    var pin       by remember { mutableStateOf("") }
    var wrong     by remember { mutableStateOf(false) }

    val shake = remember { Animatable(0f) }
    LaunchedEffect(wrong) {
        if (wrong) {
            shake.animateTo(14f, tween(60))
            shake.animateTo(-14f, tween(60))
            shake.animateTo(10f, tween(60))
            shake.animateTo(-10f, tween(60))
            shake.animateTo(0f, tween(60))
            delay(400)
            pin = ""
            wrong = false
        }
    }

    fun onDigit(d: String) {
        if (pin.length >= 6) return
        val next = pin + d
        pin = next
        if (next.length == 6) {
            // Demo rule: unlock if every digit equals the first
            if (next.all { it == next[0] } || next == "141414") onUnlock()
            else wrong = true
        }
    }

    fun onBackspace() {
        if (pin.isNotEmpty()) pin = pin.dropLast(1)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AuthBackdrop()

        Column(
            modifier             = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 28.dp),
            horizontalAlignment  = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            KotoLogoTile(size = 80.dp)
            Spacer(Modifier.size(20.dp))
            Text(
                text       = "Koto заблокирован",
                style      = KotoTheme.typography.headlineMedium,
                color      = colors.text,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text      = "Введите PIN-код, чтобы разблокировать переписки",
                style     = KotoTheme.typography.bodyMedium,
                color     = colors.textSecondary,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.size(36.dp))

            // Dot row
            Row(
                modifier              = Modifier.graphicsLayer { translationX = shake.value },
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                repeat(6) { i ->
                    val filled = i < pin.length
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    wrong  -> colors.error
                                    filled -> colors.accent
                                    else   -> colors.separator
                                },
                            ),
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Keypad (3 × 4 grid)
            val rows = listOf(
                listOf("1","2","3"),
                listOf("4","5","6"),
                listOf("7","8","9"),
                listOf("",  "0",  "⌫"),
            )
            Column(
                verticalArrangement  = Arrangement.spacedBy(18.dp),
                modifier             = Modifier.fillMaxWidth(),
                horizontalAlignment  = Alignment.CenterHorizontally,
            ) {
                rows.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        row.forEach { label ->
                            Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                                when (label) {
                                    ""   -> {}
                                    "⌫"  -> KeypadButton(label = label, onClick = ::onBackspace)
                                    else -> KeypadButton(label = label, onClick = { onDigit(label) })
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.size(40.dp))
        }
    }
}

@Composable
private fun KeypadButton(label: String, onClick: () -> Unit) {
    val colors = KotoTheme.colors
    Box(
        modifier          = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(colors.surface.copy(alpha = 0.35f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = ripple(bounded = true, radius = 36.dp),
                onClick           = onClick,
            ),
        contentAlignment  = Alignment.Center,
    ) {
        if (label == "⌫") {
            Text(text = label, style = KotoTheme.typography.headlineSmall, color = colors.text)
        } else {
            Text(
                text       = label,
                style      = KotoTheme.typography.displayMedium,
                color      = colors.text,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}
