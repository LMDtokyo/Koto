package run.koto.desktop.ui.screens.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import run.koto.desktop.ui.icons.KotoIcons
import run.koto.desktop.ui.theme.KotoEasing
import run.koto.desktop.ui.theme.KotoTheme

/**
 * Three-step account creation flow. Matches `RegisterScreen` from the mockup:
 *   Step 0 — display the 12-word BIP39 recovery phrase + "copy" + warning card
 *   Step 1 — verify user wrote them down: pick correct word for positions 3, 7, 11
 *   Step 2 — choose display name, show derived Koto ID preview
 *
 * The seed is generated once on entry and held constant across steps so going
 * "back" doesn't regenerate it.
 */
@Composable
fun RegisterScreen(
    viewModel : AuthViewModel,
    onBack    : () -> Unit,
) {
    val colors = KotoTheme.colors
    val state by viewModel.state.collectAsState()

    val seed = remember { pickSeed() }
    var kotoId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(seed) { kotoId = viewModel.previewKotoId(seed) }
    var step by remember { mutableStateOf(RegisterStep.ShowSeed) }
    var name by remember { mutableStateOf("") }

    // Step 1 verification state
    val positions = remember { listOf(2, 6, 10) }
    val confirmed = remember { mutableStateListOf<String>() }
    var confirmWrong by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        AuthBackdrop()

        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top bar: back + step indicator ────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                BackChip(
                    label   = "Назад",
                    onClick = {
                        when (step) {
                            RegisterStep.ShowSeed     -> onBack()
                            RegisterStep.ConfirmSeed  -> { step = RegisterStep.ShowSeed; confirmed.clear() }
                            RegisterStep.EnterName    -> step = RegisterStep.ConfirmSeed
                        }
                    },
                )
                Text(
                    text       = "шаг ${step.ordinal + 1} из 3",
                    style      = KotoTheme.typography.labelMedium,
                    color      = colors.textSecondary,
                    fontWeight = FontWeight.SemiBold,
                    modifier   = Modifier.weight(1f),
                    textAlign  = TextAlign.Center,
                )
                Spacer(Modifier.size(100.dp))
            }

            // ── Progress bar ───────────────────────────────────────────────
            // Segment fill animates via animateColorAsState so the accent-color
            // sweep matches the step slide below — they feel like one motion.
            Row(
                modifier              = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 2.dp).height(3.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                (0..2).forEach { i ->
                    val filled = i <= step.ordinal
                    val barColor by animateColorAsState(
                        targetValue   = if (filled) colors.accent else colors.separator,
                        animationSpec = tween(durationMillis = 320, easing = KotoEasing),
                        label         = "progress-$i",
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(barColor),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Scrollable step body ───────────────────────────────────────
            Box(
                modifier          = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment  = Alignment.TopCenter,
            ) {
                Column(
                    modifier            = Modifier
                        .widthIn(max = 460.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Step content crossfades + horizontally slides; direction
                    // tracks navigation — forward slides in from the right,
                    // back slides in from the left. Subtle (window/6 offset)
                    // so it reads as hand-off, not a transition gimmick.
                    AnimatedContent(
                        targetState    = step,
                        transitionSpec = {
                            val forward = targetState.ordinal > initialState.ordinal
                            val enter   = slideInHorizontally(
                                animationSpec = tween(320, easing = KotoEasing),
                            ) { full -> if (forward) full / 6 else -full / 6 } + fadeIn(tween(240))
                            val exit    = slideOutHorizontally(
                                animationSpec = tween(280, easing = KotoEasing),
                            ) { full -> if (forward) -full / 8 else full / 8 } + fadeOut(tween(180))
                            enter togetherWith exit
                        },
                        label          = "register-step",
                    ) { currentStep ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            when (currentStep) {
                                RegisterStep.ShowSeed     -> ShowSeedStep(seed = seed)
                                RegisterStep.ConfirmSeed  -> ConfirmSeedStep(
                                    seed          = seed,
                                    positions     = positions,
                                    confirmed     = confirmed.toList(),
                                    wrongShake    = confirmWrong,
                                    onPickChoice  = { pick ->
                                        val expected = seed[positions[confirmed.size]]
                                        if (pick == expected) {
                                            confirmed += pick
                                            confirmWrong = false
                                        } else {
                                            confirmWrong = true
                                        }
                                    },
                                    resetWrong    = { confirmWrong = false },
                                )
                                RegisterStep.EnterName    -> EnterNameStep(
                                    name         = name,
                                    onNameChange = { name = it.take(32) },
                                    kotoId       = kotoId,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }

            // ── Bottom action ───────────────────────────────────────────────
            // Outer Box centers; inner Box caps the button width on wide
            // screens so it doesn't stretch into "banner" territory.
            Box(
                modifier         = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth()) {
                    val allConfirmed = confirmed.size == positions.size
                    when (step) {
                        RegisterStep.ShowSeed -> PrimaryButton(
                            label   = "я записал фразу",
                            onClick = { step = RegisterStep.ConfirmSeed },
                        )
                        RegisterStep.ConfirmSeed -> if (allConfirmed) {
                            PrimaryButton(
                                label   = "продолжить",
                                onClick = { step = RegisterStep.EnterName },
                            )
                        }
                        RegisterStep.EnterName -> {
                            val loading = state is AuthViewModel.State.Loading
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                PrimaryButton(
                                    label   = if (loading) "регистрация…" else "войти в Koto",
                                    enabled = !loading && name.trim().isNotEmpty(),
                                    onClick = { viewModel.register(name.trim(), seed) },
                                )
                                if (state is AuthViewModel.State.Error) {
                                    Text(
                                        text     = (state as AuthViewModel.State.Error).msg,
                                        style    = KotoTheme.typography.bodySmall,
                                        color    = colors.error,
                                        modifier = Modifier.padding(top = 10.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Step 0 ─────────────────────────────────────────────────────────────────

@Composable
private fun ShowSeedStep(seed: List<String>) {
    val colors = KotoTheme.colors
    val scope  = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }

    // Single entrance pulse — fade + gentle rise. Animatable is cheap and
    // keyed to composition, so switching steps and coming back replays it.
    val cardAlpha = remember { Animatable(0f) }
    val cardY     = remember { Animatable(6f) }
    LaunchedEffect(Unit) {
        scope.launch { cardAlpha.animateTo(1f, tween(360, easing = KotoEasing)) }
        scope.launch { cardY    .animateTo(0f, tween(360, easing = KotoEasing)) }
    }

    KotoLogoTile(size = 56.dp)
    Spacer(Modifier.height(12.dp))
    Text(
        text       = "Ваша фраза\nвосстановления",
        style      = KotoTheme.typography.headlineLarge,
        color      = colors.text,
        textAlign  = TextAlign.Center,
        fontWeight = FontWeight.ExtraBold,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text      = "$SEED_WORD_COUNT слов — это и есть ваш аккаунт. Запишите их по порядку. Кто знает фразу — управляет Koto ID.",
        style     = KotoTheme.typography.bodySmall,
        color     = colors.textSecondary,
        textAlign = TextAlign.Center,
    )

    Spacer(Modifier.height(14.dp))

    // Two-column seed grid — fades + rises in as a single block (cheaper than
    // per-cell stagger and more legible at this tile density).
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = cardAlpha.value; translationY = cardY.value }
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface.copy(alpha = 0.72f))
            .border(1.dp, colors.separator, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        val half = (seed.size + 1) / 2
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (r in 0 until half) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    listOf(r, r + half).forEach { idx ->
                        if (idx < seed.size) {
                            SeedWordCell(
                                idx     = idx + 1,
                                word    = seed[idx],
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(4.dp))

    // "Copy phrase" action
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    Box(
        modifier          = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = ripple(bounded = true),
                onClick           = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(seed.joinToString(" ")))
                    scope.launch {
                        copied = true
                        delay(1500)
                        copied = false
                    }
                },
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment  = Alignment.Center,
    ) {
        Text(
            text       = if (copied) "✓ скопировано" else "скопировать фразу",
            style      = KotoTheme.typography.bodyMedium,
            color      = colors.accent,
            fontWeight = FontWeight.Medium,
        )
    }

    Spacer(Modifier.height(4.dp))

    // Warning card
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.error.copy(alpha = 0.12f))
            .border(0.5.dp, colors.error.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text(text = "⚠", style = KotoTheme.typography.titleMedium)
        Text(
            text  = "Никогда не пересылайте фразу в чатах, почте или облаке. Восстановление невозможно.",
            style = KotoTheme.typography.bodySmall,
            color = colors.error,
        )
    }
}

@Composable
private fun SeedWordCell(idx: Int, word: String, modifier: Modifier = Modifier) {
    Row(
        modifier              = modifier,
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text      = idx.toString().padStart(2, ' '),
            style     = KotoTheme.typography.monoSmall,
            color     = KotoTheme.colors.textSecondary,
            textAlign = TextAlign.End,
            modifier  = Modifier.width(18.dp),
        )
        Text(
            text       = word,
            style      = KotoTheme.typography.bodyMedium,
            color      = KotoTheme.colors.text,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ─── Step 1 ─────────────────────────────────────────────────────────────────

@Composable
private fun ConfirmSeedStep(
    seed         : List<String>,
    positions    : List<Int>,
    confirmed    : List<String>,
    wrongShake   : Boolean,
    onPickChoice : (String) -> Unit,
    resetWrong   : () -> Unit,
) {
    val colors = KotoTheme.colors
    val allDone = confirmed.size == positions.size

    // Shake offset resets automatically after 600 ms via LaunchedEffect.
    val shakeX by animateDpAsState(
        targetValue   = if (wrongShake) 0.dp else 0.dp,
        animationSpec = tween(600),
        label         = "shake-x",
    )
    LaunchedEffect(wrongShake) {
        if (wrongShake) { delay(600); resetWrong() }
    }

    if (allDone) {
        Spacer(Modifier.height(60.dp))
        Box(
            modifier          = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(colors.success),
            contentAlignment  = Alignment.Center,
        ) {
            Icon(
                imageVector        = KotoIcons.Check,
                contentDescription = null,
                tint               = Color.White,
                modifier           = Modifier.size(34.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text       = "фраза подтверждена",
            style      = KotoTheme.typography.headlineSmall,
            color      = colors.text,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text  = "Koto ID готов к использованию",
            style = KotoTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
        return
    }

    val targetIdx = positions[confirmed.size]
    val targetWord = seed[targetIdx]

    // Shuffle 3 fake words + target word, deterministic per round
    val choices = remember(confirmed.size) {
        val fakes = KOTO_WORDS.filterNot { it in seed }.shuffled().take(3)
        (fakes + targetWord).shuffled()
    }

    Text(
        text       = "Сверим фразу",
        style      = KotoTheme.typography.headlineLarge,
        color      = colors.text,
        textAlign  = TextAlign.Center,
        fontWeight = FontWeight.ExtraBold,
    )
    Spacer(Modifier.height(10.dp))
    Text(
        text      = "Выберите слово, которое стоит на указанной позиции.",
        style     = KotoTheme.typography.bodySmall,
        color     = colors.textSecondary,
        textAlign = TextAlign.Center,
    )

    Spacer(Modifier.height(22.dp))

    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surface.copy(alpha = 0.72f))
            .border(1.dp, colors.separator, RoundedCornerShape(20.dp))
            .padding(vertical = 22.dp, horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text       = "слово",
            style      = KotoTheme.typography.labelSmall,
            color      = colors.textSecondary,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text       = "#${targetIdx + 1}",
            style      = KotoTheme.typography.displayLarge,
            color      = colors.accent,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text  = "${confirmed.size}/${positions.size} подтверждено",
            style = KotoTheme.typography.labelMedium,
            color = colors.textSecondary,
        )
    }

    Spacer(Modifier.height(14.dp))

    Column(
        modifier            = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        choices.forEach { word ->
            ChoiceButton(
                label    = word,
                wrong    = wrongShake,
                onClick  = { onPickChoice(word) },
            )
        }
    }

    if (wrongShake) {
        Text(
            text     = "не совпадает, попробуйте ещё раз",
            style    = KotoTheme.typography.bodySmall,
            color    = colors.error,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun ChoiceButton(label: String, wrong: Boolean, onClick: () -> Unit) {
    val colors = KotoTheme.colors
    Box(
        modifier          = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (wrong) colors.error.copy(alpha = 0.1f)
                else colors.surface.copy(alpha = 0.7f),
            )
            .border(1.dp, colors.separator, RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = ripple(bounded = true),
                onClick           = onClick,
            )
            .padding(vertical = 16.dp),
        contentAlignment  = Alignment.Center,
    ) {
        Text(
            text       = label,
            style      = KotoTheme.typography.titleLarge,
            color      = colors.text,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ─── Step 2 ─────────────────────────────────────────────────────────────────

@Composable
private fun EnterNameStep(
    name         : String,
    onNameChange : (String) -> Unit,
    kotoId       : String?,
) {
    val colors = KotoTheme.colors

    KotoLogoTile(size = 64.dp)
    Spacer(Modifier.height(18.dp))
    Text(
        text       = "Как вас зовут?",
        style      = KotoTheme.typography.headlineLarge,
        color      = colors.text,
        textAlign  = TextAlign.Center,
        fontWeight = FontWeight.ExtraBold,
    )
    Spacer(Modifier.height(10.dp))
    Text(
        text      = "Имя видят только те, кому вы пишете. На серверах — никогда.",
        style     = KotoTheme.typography.bodySmall,
        color     = colors.textSecondary,
        textAlign = TextAlign.Center,
    )

    Spacer(Modifier.height(26.dp))

    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface.copy(alpha = 0.72f))
            .border(1.dp, colors.separator, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier          = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(colors.accentGradient),
            contentAlignment  = Alignment.Center,
        ) {
            Text(
                text       = (name.firstOrNull()?.uppercaseChar() ?: '?').toString(),
                color      = Color.White,
                style      = KotoTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            if (name.isEmpty()) {
                Text(
                    text       = "Ваше имя",
                    style      = KotoTheme.typography.headlineSmall,
                    color      = colors.textTertiary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            BasicTextField(
                value         = name,
                onValueChange = onNameChange,
                singleLine    = true,
                textStyle     = LocalTextStyle.current.merge(
                    KotoTheme.typography.headlineSmall.copy(
                        color = colors.text,
                    ),
                ).copy(fontWeight = FontWeight.SemiBold),
                cursorBrush   = SolidColor(colors.accent),
                modifier      = Modifier.fillMaxWidth(),
            )
        }
    }

    Spacer(Modifier.height(14.dp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface.copy(alpha = 0.55f))
            .border(1.dp, colors.separator, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Text(
            text       = "ВАШ KOTO ID",
            style      = KotoTheme.typography.labelSmall,
            color      = colors.textSecondary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text     = kotoId ?: "вычисляем…",
            style    = KotoTheme.typography.mono,
            color    = if (kotoId == null) colors.textSecondary else colors.text,
        )
    }
}

