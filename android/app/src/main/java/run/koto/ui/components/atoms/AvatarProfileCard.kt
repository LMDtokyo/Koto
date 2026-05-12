package run.koto.ui.components.atoms

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import run.koto.domain.model.ConversationUi
import run.koto.ui.theme.KotoTheme

/**
 * MI-04: Popup profile card triggered by long-press on an avatar.
 *
 * Entry: spring scale-up from 0% using KotoTheme.motion.springEmphasized.
 * Exit:  spring scale-down to 0% using KotoTheme.motion.springMicro.
 * Backdrop: API 31+ blur(20px) via RenderEffect; below API 31 = black 60% scrim.
 *
 * Must be placed in a Box that covers the full screen so the backdrop works.
 * The card is centered in that Box.
 *
 * MI-05 compliance: all specs via KotoTheme.motion — no raw spring() literals.
 */
@Composable
fun AvatarProfileCard(
    conv      : ConversationUi,
    onDismiss : () -> Unit,
    onMessage : () -> Unit,
    onCall    : () -> Unit,
    onProfile : () -> Unit,
    modifier  : Modifier = Modifier,
) {
    val colors = KotoTheme.colors
    val motion = KotoTheme.motion

    // Trigger enter animation on first composition
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    // Full-screen backdrop — tap to dismiss
    val backdropMod = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.graphicsLayer {
            renderEffect = android.graphics.RenderEffect
                .createBlurEffect(20f, 20f, android.graphics.Shader.TileMode.CLAMP)
                .asComposeRenderEffect()
        }
    } else {
        Modifier.background(Color.Black.copy(alpha = 0.60f))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(backdropMod)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter   = scaleIn(
                animationSpec   = motion.springEmphasized,
                transformOrigin = TransformOrigin.Center,
            ) + fadeIn(tween(120)),
            exit    = scaleOut(
                animationSpec   = motion.springMicro,
                transformOrigin = TransformOrigin.Center,
            ) + fadeOut(tween(80)),
        ) {
            Surface(
                modifier        = Modifier
                    .widthIn(min = 260.dp, max = 280.dp)
                    .clickable { /* consume clicks so card taps don't dismiss */ },
                shape           = KotoTheme.shapes.lg,
                color           = colors.surface,
                tonalElevation  = 8.dp,
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier            = Modifier.padding(
                        horizontal = KotoTheme.spacing.xl,
                        vertical   = KotoTheme.spacing.xl,
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(KotoTheme.spacing.md),
                ) {
                    // 80dp avatar with deterministic gradient initials
                    val gradientColors = remember(conv.id) { avatarGradientForCard(conv.id) }
                    Box(
                        modifier         = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(gradientColors)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text       = conv.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                            style      = KotoTheme.typography.titleLarge,
                            color      = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    // Display name
                    Text(
                        text       = conv.displayName,
                        style      = KotoTheme.typography.titleLarge,
                        color      = colors.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )

                    // Online status
                    Text(
                        text  = if (conv.isOnline) "В сети" else "Не в сети",
                        style = KotoTheme.typography.bodyMedium,
                        color = if (conv.isOnline) colors.online else colors.onSurfaceMuted,
                    )

                    // Primary action: Message
                    Button(
                        onClick  = onMessage,
                        modifier = Modifier.fillMaxWidth(),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = colors.primary,
                            contentColor   = colors.onPrimary,
                        ),
                        shape = KotoTheme.shapes.md,
                    ) {
                        Icon(
                            imageVector        = Icons.Default.Chat,
                            contentDescription = null,
                            modifier           = Modifier.size(18.dp),
                        )
                        Text(
                            text     = "Написать",
                            modifier = Modifier.padding(start = KotoTheme.spacing.xs),
                        )
                    }

                    // Secondary actions: Call + Profile
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(KotoTheme.spacing.sm),
                    ) {
                        OutlinedButton(
                            onClick  = onCall,
                            modifier = Modifier.weight(1f),
                            shape    = KotoTheme.shapes.md,
                        ) {
                            Icon(
                                imageVector        = Icons.Default.Call,
                                contentDescription = "Позвонить",
                                modifier           = Modifier.size(18.dp),
                            )
                        }
                        OutlinedButton(
                            onClick  = onProfile,
                            modifier = Modifier.weight(1f),
                            shape    = KotoTheme.shapes.md,
                        ) {
                            Icon(
                                imageVector        = Icons.Default.Person,
                                contentDescription = "Профиль",
                                modifier           = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// Gradient palette for the 80dp avatar — same deterministic logic as ConversationItem
private val cardGradientPairs = listOf(
    listOf(Color(0xFF7C3AED), Color(0xFF5B21B6)),
    listOf(Color(0xFF0EA5E9), Color(0xFF0369A1)),
    listOf(Color(0xFF10B981), Color(0xFF047857)),
    listOf(Color(0xFFF59E0B), Color(0xFFB45309)),
    listOf(Color(0xFFEF4444), Color(0xFFB91C1C)),
)

private fun avatarGradientForCard(id: String): List<Color> =
    cardGradientPairs[Math.abs(id.hashCode()) % cardGradientPairs.size]
