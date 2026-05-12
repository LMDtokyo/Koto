package run.koto.desktop.ui.screens.safety

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import run.koto.desktop.ui.icons.KotoIcons
import run.koto.desktop.ui.screens.others.QrCode
import run.koto.desktop.ui.theme.KotoTheme

/**
 * Per-contact safety detail — Signal-style. Shows the 60-digit safety number
 * (12 groups × 5 digits in a 4×3 grid), a QR encoding the same payload, and a
 * toggle to mark the contact as verified. The number is deterministic from
 * the local + peer identity ids, so both peers compute the exact same string.
 */
@Composable
fun SafetyDetailScreen(
    convId    : String,
    viewModel : SafetyDetailViewModel,
    onBack    : () -> Unit,
) {
    val colors = KotoTheme.colors
    val state by viewModel.state.collectAsState()

    LaunchedEffect(convId) { viewModel.load(convId) }
    DisposableEffect(Unit) { onDispose { viewModel.close() } }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top bar ───────────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 14.dp),
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
                        .padding(vertical = 6.dp, horizontal = 6.dp),
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
                    text       = "Проверка безопасности",
                    style      = KotoTheme.typography.titleLarge,
                    color      = colors.text,
                    fontWeight = FontWeight.SemiBold,
                    textAlign  = TextAlign.Center,
                    modifier   = Modifier.weight(1f),
                )
                Spacer(Modifier.size(96.dp))
            }
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(colors.separator))

            // ── Body ──────────────────────────────────────────────────────
            Column(
                modifier            = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (state.loading) {
                    Box(
                        modifier         = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.surface)
                            .padding(28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("загружаем…", style = KotoTheme.typography.bodyMedium, color = colors.textSecondary)
                    }
                    return@Column
                }

                IntroBlock(displayName = state.displayName)
                Spacer(Modifier.height(16.dp))

                if (state.safetyNumber.isNotBlank()) {
                    QrCard(payload = state.safetyShareText)
                    Spacer(Modifier.height(16.dp))
                    SafetyNumberCard(number = state.safetyNumber)
                    Spacer(Modifier.height(20.dp))
                    VerifiedToggleRow(
                        verified  = state.isVerified,
                        onChange  = viewModel::setVerified,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text     = "Включите, когда сравнили номер с собеседником лично или по другому защищённому каналу. Зелёная галочка появится напротив имени в списках.",
                        style    = KotoTheme.typography.bodySmall,
                        color    = colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                } else {
                    Box(
                        modifier         = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.surface)
                            .padding(20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text  = "Не удалось получить идентификаторы для сравнения. Попробуйте позже.",
                            style = KotoTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun IntroBlock(displayName: String) {
    val colors = KotoTheme.colors
    Column(
        modifier            = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier          = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
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
        Spacer(Modifier.height(12.dp))
        Text(
            text       = "Номер безопасности",
            style      = KotoTheme.typography.headlineMedium,
            color      = colors.text,
            fontWeight = FontWeight.SemiBold,
            textAlign  = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text      = "Сравните этот номер с $displayName, чтобы убедиться, что между вами никто не вклинился. Совпал — значит шифрование честное.",
            style     = KotoTheme.typography.bodyMedium,
            color     = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier  = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun QrCard(payload: String) {
    val colors = KotoTheme.colors
    Box(
        modifier         = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier         = Modifier
                .size(220.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            QrCode(
                text     = payload,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SafetyNumberCard(number: String) {
    val colors = KotoTheme.colors
    val groups = number.split(' ').filter { it.isNotBlank() }
    val rows   = groups.chunked(3) // 12 groups → 4 rows × 3 groups

    Column(
        modifier            = Modifier
            .fillMaxWidth()
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
        rows.forEach { row ->
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                row.forEach { g ->
                    Text(
                        text       = g,
                        style      = KotoTheme.typography.monoLarge,
                        color      = colors.text,
                        fontWeight = FontWeight.Medium,
                        textAlign  = TextAlign.Center,
                        modifier   = Modifier.weight(1f),
                    )
                }
                // Pad short trailing rows so columns stay aligned.
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun VerifiedToggleRow(verified: Boolean, onChange: (Boolean) -> Unit) {
    val colors = KotoTheme.colors
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = ripple(bounded = true),
                onClick           = { onChange(!verified) },
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = "Контакт проверен",
                style      = KotoTheme.typography.bodyLarge,
                color      = colors.text,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text     = if (verified) "Зелёная галочка показывается рядом с именем." else "Включите, когда сверили номер.",
                style    = KotoTheme.typography.bodySmall,
                color    = colors.textSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        IosToggle(value = verified, onChange = onChange)
    }
}

/** iOS-style toggle, mirrored from PrivacyScreen so the screen stays self-contained. */
@Composable
private fun IosToggle(value: Boolean, onChange: (Boolean) -> Unit) {
    val colors  = KotoTheme.colors
    val trackBg by animateColorAsState(
        targetValue = if (value) colors.accent else colors.separator,
        animationSpec = tween(220),
        label       = "track-bg",
    )
    val thumbX by animateDpAsState(
        targetValue   = if (value) 18.dp else 0.dp,
        animationSpec = tween(220),
        label         = "thumb-x",
    )
    Box(
        modifier = Modifier
            .size(width = 46.dp, height = 28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(trackBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = { onChange(!value) },
            ),
    ) {
        Box(
            modifier = Modifier
                .padding(2.dp)
                .offset(x = thumbX)
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}
