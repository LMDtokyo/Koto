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
import androidx.compose.foundation.layout.height
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
import run.koto.desktop.ui.components.atoms.Avatar
import run.koto.desktop.ui.icons.KotoIcons
import run.koto.desktop.ui.screens.chatlist.MockData
import run.koto.desktop.ui.theme.KotoTheme

/**
 * Contact profile screen — full-pane view with large avatar, name, status,
 * big action row (Message / Call / Video / Safety), followed by stacked card
 * sections: About, Phone-style rows, Shared media stub, Safety, danger zone.
 */
@Composable
fun ContactScreen(
    contactId  : String,
    onBack     : () -> Unit,
    onOpenChat : (String) -> Unit,
    onOpenCall : () -> Unit,
    onOpenVideo: () -> Unit,
) {
    val colors  = KotoTheme.colors
    val contact = remember(contactId) { MockData.contact(contactId) }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // Back chip
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
                    Text(text = "Чат", style = KotoTheme.typography.titleMedium, color = colors.accent)
                }
            }

            // Hero
            Column(
                modifier             = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment  = Alignment.CenterHorizontally,
            ) {
                Avatar(
                    initials = contact.initials,
                    color    = contact.color,
                    size     = 120.dp,
                    pulse    = contact.status == "в сети",
                    online   = contact.status == "в сети",
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text       = contact.name,
                    style      = KotoTheme.typography.displayMedium,
                    color      = colors.text,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign  = TextAlign.Center,
                )
                Text(
                    text      = contact.status.ifEmpty { contact.meta },
                    style     = KotoTheme.typography.bodyMedium,
                    color     = colors.textSecondary,
                    modifier  = Modifier.padding(top = 4.dp),
                )
            }

            // Action row
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                ActionChip(icon = KotoIcons.Phone,  label = "Голос",  onClick = onOpenCall)
                ActionChip(icon = KotoIcons.Video,  label = "Видео",  onClick = onOpenVideo)
                ActionChip(icon = KotoIcons.Pencil, label = "Сообщ.", onClick = { onOpenChat(contact.id) })
                ActionChip(icon = KotoIcons.Shield, label = "Безоп.", onClick = { /* push SafetyScreen */ })
            }

            // About
            ProfileCardSection(title = "О СЕБЕ") {
                Text(
                    text  = "«Технологии — это поэзия в действии. Строю быстрее всех.» 🧡",
                    style = KotoTheme.typography.bodyMedium,
                    color = colors.text,
                )
            }

            // Details
            ProfileCardSection(title = "KOTO ID") {
                Text(
                    text  = contact.kotoId,
                    style = KotoTheme.typography.mono,
                    color = colors.text,
                )
            }

            ProfileCardSection(title = "ПРИВАТНОСТЬ") {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    InfoRow(label = "Общие чаты", value = "—")
                    InfoRow(label = "Контакты",   value = if (contact.isGroup) "группа" else "добавлен")
                    InfoRow(label = "Шифрование", value = "PQXDH · Signal Protocol")
                }
            }

            // Danger zone
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surface),
            ) {
                DangerRow(
                    icon  = KotoIcons.Mute,
                    label = "Отключить уведомления",
                    color = colors.text,
                )
                Divider()
                DangerRow(
                    icon  = KotoIcons.Alert,
                    label = "Пожаловаться",
                    color = colors.warning,
                )
                Divider()
                DangerRow(
                    icon  = KotoIcons.Close,
                    label = "Заблокировать",
                    color = colors.error,
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun ActionChip(
    icon    : androidx.compose.ui.graphics.vector.ImageVector,
    label   : String,
    onClick : () -> Unit,
) {
    val colors = KotoTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = ripple(bounded = true),
                onClick           = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Box(
            modifier          = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(colors.accent.copy(alpha = 0.14f)),
            contentAlignment  = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
        }
        Text(text = label, style = KotoTheme.typography.labelMedium, color = colors.accent)
    }
}

@Composable
private fun ProfileCardSection(title: String, content: @Composable () -> Unit) {
    val colors = KotoTheme.colors
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Text(
            text       = title,
            style      = KotoTheme.typography.labelMedium,
            color      = colors.textSecondary,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface)
                .padding(14.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = KotoTheme.typography.bodyMedium, color = KotoTheme.colors.text)
        Text(text = value, style = KotoTheme.typography.bodyMedium, color = KotoTheme.colors.textSecondary)
    }
}

@Composable
private fun DangerRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = ripple(bounded = true),
                onClick           = { /* placeholder */ },
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(
            text       = label,
            style      = KotoTheme.typography.bodyLarge,
            color      = color,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun Divider() {
    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(KotoTheme.colors.separator))
}
