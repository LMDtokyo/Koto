package run.koto.desktop.ui.screens.others

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import run.koto.desktop.ui.icons.KotoIcons
import run.koto.desktop.ui.theme.KotoTheme

/**
 * Modal sheet — share-link, copy-id and a short instruction. The sheet
 * dismisses on outside tap; close button is provided for keyboard accessibility.
 */
@Composable
fun InviteSheet(
    accountId : String,
    onDismiss : () -> Unit,
) {
    val colors    = KotoTheme.colors
    val clipboard = LocalClipboardManager.current
    val link      = remember(accountId) { "koto://chat/$accountId" }
    var copied    by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(copied) {
        if (copied != null) { delay(1500); copied = null }
    }

    DimmedScrim(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surface)
                .padding(20.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text       = "Пригласить в Koto",
                    style      = KotoTheme.typography.titleLarge,
                    color      = colors.text,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier.weight(1f),
                )
                CloseButton(onClick = onDismiss)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text  = "Поделитесь своим Koto ID или ссылкой — собеседник установит Koto и сразу попадёт в чат с вами.",
                style = KotoTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(18.dp))

            CopyRow(
                label  = "Koto ID",
                value  = accountId,
                copied = copied == "id",
                onCopy = {
                    clipboard.setText(AnnotatedString(accountId))
                    copied = "id"
                },
            )
            Spacer(Modifier.height(10.dp))
            CopyRow(
                label  = "Ссылка",
                value  = link,
                copied = copied == "link",
                onCopy = {
                    clipboard.setText(AnnotatedString(link))
                    copied = "link"
                },
            )
        }
    }
}

@Composable
fun QrSheet(
    accountId : String,
    onDismiss : () -> Unit,
) {
    val colors    = KotoTheme.colors
    val clipboard = LocalClipboardManager.current
    val link      = remember(accountId) { "koto://chat/$accountId" }
    var copied    by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) { delay(1500); copied = false }
    }

    DimmedScrim(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surface)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text       = "Мой QR-код",
                    style      = KotoTheme.typography.titleLarge,
                    color      = colors.text,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier.weight(1f),
                )
                CloseButton(onClick = onDismiss)
            }
            Spacer(Modifier.height(12.dp))
            Box(
                modifier         = Modifier
                    .size(260.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                QrCode(
                    text       = link,
                    modifier   = Modifier.fillMaxSize(),
                    foreground = Color(0xFF111111),
                    background = Color.White,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text      = "Дайте отсканировать этот код в Koto-приложении на телефоне.",
                style     = KotoTheme.typography.bodySmall,
                color     = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            CopyRow(
                label  = "Koto ID",
                value  = accountId,
                copied = copied,
                onCopy = {
                    clipboard.setText(AnnotatedString(accountId))
                    copied = true
                },
            )
        }
    }
}

@Composable
private fun DimmedScrim(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Inner box swallows clicks so taps on the sheet don't dismiss.
        Box(
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = {},
            ),
            content = { content() },
        )
    }
}

@Composable
private fun CopyRow(label: String, value: String, copied: Boolean, onCopy: () -> Unit) {
    val colors = KotoTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text       = label,
            style      = KotoTheme.typography.labelMedium,
            color      = colors.textSecondary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.background)
                .border(1.dp, colors.separator, RoundedCornerShape(10.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = ripple(bounded = true),
                    onClick           = onCopy,
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text     = value,
                style    = KotoTheme.typography.monoSmall,
                color    = colors.text,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                text       = if (copied) "скопировано ✓" else "копировать",
                style      = KotoTheme.typography.labelMedium,
                color      = if (copied) colors.accent else colors.textSecondary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun CloseButton(onClick: () -> Unit) {
    val colors = KotoTheme.colors
    Box(
        modifier         = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = ripple(bounded = true),
                onClick           = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector        = KotoIcons.Close,
            contentDescription = null,
            tint               = colors.textSecondary,
            modifier           = Modifier.size(16.dp),
        )
    }
}
