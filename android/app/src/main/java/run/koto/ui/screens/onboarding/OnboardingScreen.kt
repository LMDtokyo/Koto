package run.koto.ui.screens.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import run.koto.R
import run.koto.ui.theme.*

@Composable
fun OnboardingScreen(
    viewModel    : OnboardingViewModel = hiltViewModel(),
    onRegistered : () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.registered) { if (state.registered) onRegistered() }

    Box(
        modifier         = Modifier.fillMaxSize().background(BgPrimary),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState  = state.step,
            transitionSpec = {
                fadeIn(tween(300)) togetherWith fadeOut(tween(200))
            },
            label = "step",
        ) { step ->
            when (step) {
                OnboardingStep.Welcome       -> WelcomePage(onStart = viewModel::startKeyGen)
                OnboardingStep.GeneratingKey -> GeneratingKeyPage()
                OnboardingStep.ShowAccountId -> ShowAccountIdPage(
                    accountId  = state.accountId,
                    onContinue = viewModel::confirmAccountId,
                )
            }
        }
    }
}

// ─── Welcome Page — Signal style ──────────────────────────────────────────────

@Composable
private fun WelcomePage(onStart: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(80); visible = true }

    AnimatedVisibility(
        visible = visible,
        enter   = fadeIn(tween(400)) + slideInVertically(tween(400, easing = EaseOutCubic)) { 40 },
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Spacer(Modifier.height(56.dp))

            // ── Cat logo — clean, no effects ─────────────────────────────────
            androidx.compose.foundation.Image(
                painter            = painterResource(id = R.drawable.ic_koto_cat),
                contentDescription = "Koto",
                modifier           = Modifier.size(140.dp),
                contentScale       = ContentScale.Fit,
            )

            Spacer(Modifier.height(32.dp))

            // ── Name ─────────────────────────────────────────────────────────
            Text(
                text          = "Koto",
                fontSize      = 40.sp,
                fontWeight    = FontWeight.Bold,
                letterSpacing = (-1).sp,
                color         = TextPrimary,
            )

            Spacer(Modifier.height(10.dp))

            // ── Tagline ───────────────────────────────────────────────────────
            Text(
                text      = "Сообщения — да. Ваши данные — нет.",
                style     = MaterialTheme.typography.bodyMedium,
                color     = TextSecondary,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(48.dp))

            // ── CTA button ────────────────────────────────────────────────────
            Button(
                onClick   = onStart,
                modifier  = Modifier.fillMaxWidth().height(54.dp),
                shape     = RoundedCornerShape(14.dp),
                colors    = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
            ) {
                Text(
                    "Начать",
                    fontWeight    = FontWeight.SemiBold,
                    fontSize      = 16.sp,
                    color         = Color.White,
                    letterSpacing = 0.2.sp,
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── Footer specs — 3 icons in a row ───────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                SpecBadge(Icons.Default.Lock,        "E2E шифрование")
                Dot()
                SpecBadge(Icons.Default.Person,      "Без данных")
                Dot()
                SpecBadge(Icons.Default.PhoneAndroid, "Без номера")
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text      = "Нажимая «Начать», вы принимаете условия использования.",
                style     = MaterialTheme.typography.labelSmall,
                color     = TextTertiary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SpecBadge(icon: ImageVector, label: String) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier              = Modifier.padding(horizontal = 4.dp),
    ) {
        Icon(icon, null, tint = TextTertiary, modifier = Modifier.size(12.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
    }
}

@Composable
private fun Dot() {
    Box(
        modifier = Modifier
            .size(3.dp)
            .clip(CircleShape)
            .background(TextTertiary)
    )
}

// ─── Generating Key Page ──────────────────────────────────────────────────────

@Composable
private fun GeneratingKeyPage() {
    Column(
        modifier            = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        CircularProgressIndicator(
            modifier    = Modifier.size(52.dp),
            color       = AccentPrimary,
            strokeWidth = 2.5.dp,
            trackColor  = BgSurface1,
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Генерация ключей",
                style      = MaterialTheme.typography.titleMedium,
                color      = TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Ed25519 ключевая пара создаётся на\nвашем устройстве. Сервер не получает\nприватный ключ.",
                style     = MaterialTheme.typography.bodySmall,
                color     = TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ─── Show Account ID Page ─────────────────────────────────────────────────────

@Composable
private fun ShowAccountIdPage(accountId: String, onContinue: () -> Unit) {
    Column(
        modifier            = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color(0xFF0D2818)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Check, null, tint = OnlineGreen, modifier = Modifier.size(30.dp))
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "Аккаунт создан",
                style      = MaterialTheme.typography.titleLarge,
                color      = TextPrimary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Это ваш публичный адрес.\nСообщайте его тем, кто хочет написать вам.",
                style     = MaterialTheme.typography.bodySmall,
                color     = TextSecondary,
                textAlign = TextAlign.Center,
            )
        }

        // ID card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(BgSecondary)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Key, null,
                    tint     = TextTertiary,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    "Публичный ключ (ваш адрес)",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextTertiary,
                )
            }
            Text(
                text      = accountId.chunked(8).joinToString("  "),
                style     = MaterialTheme.typography.bodySmall.copy(
                    fontFamily    = FontFamily.Monospace,
                    lineHeight    = 22.sp,
                    fontSize      = 12.sp,
                    letterSpacing = 0.5.sp,
                ),
                color     = TextPrimary,
                modifier  = Modifier.fillMaxWidth(),
            )
        }

        // Warning — честно о текущем состоянии
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1A1208))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Warning, null, tint = WarningAmber, modifier = Modifier.size(15.dp))
                Text(
                    "Приватный ключ",
                    style      = MaterialTheme.typography.labelMedium,
                    color      = WarningAmber,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                "Ваш приватный ключ хранится только на этом устройстве. При смене или потере телефона аккаунт восстановить невозможно.",
                style = MaterialTheme.typography.bodySmall,
                color = WarningAmber.copy(alpha = 0.85f),
            )
        }

        Button(
            onClick   = onContinue,
            modifier  = Modifier.fillMaxWidth().height(54.dp),
            shape     = RoundedCornerShape(14.dp),
            colors    = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
        ) {
            Text(
                "Понятно, войти",
                fontWeight = FontWeight.SemiBold,
                fontSize   = 16.sp,
                color      = Color.White,
            )
        }
    }
}
