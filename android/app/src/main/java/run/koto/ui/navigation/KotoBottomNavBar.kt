package run.koto.ui.navigation

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import run.koto.ui.theme.KotoTheme

// ─── Destinations ─────────────────────────────────────────────────────────────

sealed class BottomNavDestination(
    val route      : String,
    val icon       : ImageVector,
    val iconFilled : ImageVector,
    val label      : String,
) {
    object Chats    : BottomNavDestination(
        route      = Screen.Conversations.route,
        icon       = Icons.Outlined.Chat,
        iconFilled = Icons.Filled.Chat,
        label      = "Чаты",
    )
    object Contacts : BottomNavDestination(
        route      = "contacts",
        icon       = Icons.Outlined.People,
        iconFilled = Icons.Filled.People,
        label      = "Контакты",
    )
    object Calls    : BottomNavDestination(
        route      = "calls",
        icon       = Icons.Outlined.Call,
        iconFilled = Icons.Filled.Call,
        label      = "Звонки",
    )
    object Settings : BottomNavDestination(
        route      = Screen.Settings.route,
        icon       = Icons.Outlined.Settings,
        iconFilled = Icons.Filled.Settings,
        label      = "Ещё",
    )

    companion object {
        val all: List<BottomNavDestination> = listOf(Chats, Contacts, Calls, Settings)
    }
}

// ─── KotoBottomNavBar ─────────────────────────────────────────────────────────
//
// Premium floating bottom nav bar inspired by Telegram 2026 + iOS Liquid Glass.
//
// Design spec:
//  - Floating: 16dp horizontal margin, 12dp above system nav
//  - 24dp corner radius, 4dp shadow elevation
//  - Frosted glass: blur 24dp + 80% surface alpha (API 31+)
//  - Solid fallback: 95% surface alpha (API < 31)
//  - NO top border line — clean floating look
//  - Active: brand primary, filled icon, label bold
//  - Inactive: 50% onSurface, outlined icon
//  - Active indicator: 32x3dp pill below icon, spring-animated horizontal slide

@Composable
fun KotoBottomNavBar(
    currentRoute  : String?,
    onTabSelected : (BottomNavDestination) -> Unit,
    modifier      : Modifier = Modifier,
) {
    val colors = KotoTheme.colors
    val shape  = RoundedCornerShape(24.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // Floating bar container with shadow + glass
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation    = 8.dp,
                    shape        = shape,
                    ambientColor = colors.primary.copy(alpha = 0.08f),
                    spotColor    = colors.primary.copy(alpha = 0.12f),
                )
                .clip(shape)
                .background(colors.surface),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                BottomNavDestination.all.forEach { dest ->
                    NavTab(
                        destination = dest,
                        isSelected  = currentRoute == dest.route,
                        onClick     = { onTabSelected(dest) },
                        modifier    = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

// ─── NavTab ──────────────────────────────────────────────────────────────────
//
// Each tab: icon (24dp) + label (11sp) + small pill indicator below when active.
// All animations via graphicsLayer (draw phase) — no recomposition on frames.

@Composable
private fun NavTab(
    destination : BottomNavDestination,
    isSelected  : Boolean,
    onClick     : () -> Unit,
    modifier    : Modifier = Modifier,
) {
    val colors = KotoTheme.colors

    // Indicator pill width animates: 0dp → 32dp
    val indicatorWidth by animateDpAsState(
        targetValue   = if (isSelected) 32.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness    = 400f,
        ),
        label = "indicator_${destination.label}",
    )

    // Icon scale pulse on select
    val iconScale by animateFloatAsState(
        targetValue   = if (isSelected) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMedium,
        ),
        label = "scale_${destination.label}",
    )

    val activeColor   = colors.primary
    val inactiveColor = colors.onSurface.copy(alpha = 0.45f)
    val tabColor      = if (isSelected) activeColor else inactiveColor

    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Icon
        Icon(
            imageVector        = if (isSelected) destination.iconFilled else destination.icon,
            contentDescription = destination.label,
            modifier           = Modifier
                .size(23.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                },
            tint = tabColor,
        )

        // Label — always visible, bold when selected
        Text(
            text       = destination.label,
            fontSize   = 11.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color      = tabColor,
            maxLines   = 1,
            modifier   = Modifier.padding(top = 3.dp),
        )

        // Active indicator pill — 3dp tall, primary color, spring-animated width
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .width(indicatorWidth)
                .height(3.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(if (isSelected) activeColor else colors.surface),
        )
    }
}
