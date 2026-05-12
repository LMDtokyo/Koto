package run.koto.ui.screens.conversations

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import run.koto.domain.model.ConversationUi
import run.koto.ui.components.atoms.AvatarProfileCard
import run.koto.ui.theme.KotoTheme
import run.koto.ui.theme.avatarGradient
import androidx.compose.material3.Text

// ─── Time formatting helper ───────────────────────────────────────────────────

private val fmtTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
private val fmtDate = java.text.SimpleDateFormat("dd.MM", java.util.Locale.getDefault())

internal fun formatConvTime(ms: Long): String {
    val now = System.currentTimeMillis()
    return if (now - ms < 24 * 3_600_000L) fmtTime.format(java.util.Date(ms))
    else fmtDate.format(java.util.Date(ms))
}

// ─── Avatar gradient helper ───────────────────────────────────────────────────
// Deterministic palette from conversation ID — avatar-identity colors, not semantic tokens.

// Avatar gradients are centralised in ui/theme/Color.kt
// to guarantee the same user always renders with the same colour
// in the conversation list, chat header, and profile card.

// ─── ConversationItem composable (CL-01) ─────────────────────────────────────

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ConversationItem(
    conv                    : ConversationUi,
    onClick                 : () -> Unit,
    modifier                : Modifier = Modifier,
    sharedTransitionScope   : SharedTransitionScope? = null,
    animatedVisibilityScope : AnimatedVisibilityScope? = null,
) {
    val colors  = KotoTheme.colors
    val spacing = KotoTheme.spacing
    val typo    = KotoTheme.typography

    var showProfileCard by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = spacing.lg, vertical = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            AvatarWithPresence(
                conv                    = conv,
                onLongClick             = { showProfileCard = true },
                sharedTransitionScope   = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                // Name + timestamp row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text     = conv.displayName.ifBlank { "Пользователь Koto" },
                        style    = typo.titleMedium,
                        color    = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = spacing.xs),
                    )
                    Text(
                        text  = if (conv.updatedAt > 0L) formatConvTime(conv.updatedAt) else "",
                        style = typo.labelMedium,
                        color = if (conv.unreadCount > 0) colors.primary else colors.onSurfaceMuted,
                    )
                }
                // Preview + badge row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text     = conv.lastMessage.ifEmpty { "No messages" },
                        style    = typo.bodySmall,
                        color    = if (conv.unreadCount > 0) colors.onSurfaceLow else colors.onSurfaceMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = spacing.xs),
                    )
                    if (conv.unreadCount > 0) {
                        UnreadBadge(count = conv.unreadCount, muted = conv.isMuted)
                    }
                }
            }
        }

        if (showProfileCard) {
            AvatarProfileCard(
                conv      = conv,
                onDismiss = { showProfileCard = false },
                onMessage = { showProfileCard = false; onClick() },
                onCall    = { showProfileCard = false },
                onProfile = { showProfileCard = false },
            )
        }
    }
}

// ─── AvatarWithPresence composable ───────────────────────────────────────────

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
private fun AvatarWithPresence(
    conv                    : ConversationUi,
    onLongClick             : () -> Unit,
    sharedTransitionScope   : SharedTransitionScope? = null,
    animatedVisibilityScope : AnimatedVisibilityScope? = null,
) {
    val colors = KotoTheme.colors

    // Shared element modifier for avatar morph — only applied when both scopes are non-null (NAV-02)
    val avatarMod = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                sharedContentState      = rememberSharedContentState(key = "avatar-${conv.id}"),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform         = { _, _ ->
                    spring(dampingRatio = 0.85f, stiffness = 380f)
                },
            )
        }
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .combinedClickable(
                onClick     = { /* tap handled by parent row */ },
                onLongClick = onLongClick,
            ),
    ) {
        // Avatar gradient — derived from the peer's stable account ID so the same
        // user is rendered with the same color everywhere (list, chat header,
        // profile card). Falls back to conversation ID for group chats where
        // there is no single peer.
        val gradientSeed  = conv.peerId.ifBlank { conv.id }
        val gradientColors = remember(gradientSeed) { avatarGradient(gradientSeed) }
        Box(
            modifier = avatarMod
                .size(48.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(gradientColors)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text       = conv.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                style      = KotoTheme.typography.titleMedium,
                color      = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
        // CL-06: Online dot — 10dp green dot with 2dp white border at bottom-right
        if (conv.isOnline) {
            OnlineDot(
                modifier = Modifier.align(Alignment.BottomEnd),
                color    = colors.online,
            )
        }
    }
}

// ─── OnlineDot atom (CL-06 — pulse animation) ────────────────────────────────

@Composable
private fun OnlineDot(modifier: Modifier = Modifier, color: Color) {
    val spacing = KotoTheme.spacing
    val infiniteTransition = rememberInfiniteTransition(label = "onlinePulse")
    val scale by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = 1.25f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "onlineDotScale",
    )

    Box(
        modifier = modifier
            .size(14.dp)   // 10dp dot + 2dp border each side = 14dp total bounding box
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(Color.White)   // white border ring
            .padding(spacing.xxs)      // 2dp border width (KotoTheme.spacing.xxs = 2dp)
            .clip(CircleShape)
            .background(color),        // green dot
    )
}

// ─── UnreadBadge atom ────────────────────────────────────────────────────────

@Composable
private fun UnreadBadge(count: Int, muted: Boolean) {
    val colors = KotoTheme.colors
    val bg     = if (muted) colors.surfaceVariant else colors.primary
    val shape  = if (count < 10) CircleShape else RoundedCornerShape(10.dp)

    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
            .clip(shape)
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text  = if (count > 99) "99+" else count.toString(),
            style = KotoTheme.typography.labelSmall,
            color = if (muted) colors.onSurface else colors.onPrimary,
        )
    }
}
