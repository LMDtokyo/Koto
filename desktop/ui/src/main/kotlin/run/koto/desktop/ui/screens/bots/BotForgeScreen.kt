package run.koto.desktop.ui.screens.bots

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import run.koto.desktop.ui.icons.KotoIcons
import run.koto.desktop.ui.theme.KotoTheme

/**
 * BotForge — bot creation wizard. The React mockup has a multi-step flow
 * (metadata → commands → webhook → preview). For Phase 3.5 we implement step 1
 * fully (name/tagline/category) and stub steps 2–4 with placeholders — enough
 * for visual review without the full state machine.
 */
@Composable
fun BotForgeScreen(
    onBack : () -> Unit,
    onDone : () -> Unit,
) {
    val colors = KotoTheme.colors
    var name     by remember { mutableStateOf("") }
    var handle   by remember { mutableStateOf("") }
    var tagline  by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("AI · ассистенты") }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // Top bar
            Row(
                modifier              = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
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
                    Text(text = "Боты", style = KotoTheme.typography.titleMedium, color = colors.accent)
                }
                Text(
                    text       = "BotForge",
                    style      = KotoTheme.typography.titleLarge,
                    color      = colors.text,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier.weight(1f),
                    textAlign  = TextAlign.Center,
                )
                Spacer(Modifier.size(80.dp))
            }

            // Hero
            Column(
                modifier             = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment  = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier          = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.accent.copy(alpha = 0.14f)),
                    contentAlignment  = Alignment.Center,
                ) {
                    Icon(
                        imageVector        = KotoIcons.Bot,
                        contentDescription = null,
                        tint               = colors.accent,
                        modifier           = Modifier.size(36.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text       = "Создайте своего бота",
                    style      = KotoTheme.typography.headlineMedium,
                    color      = colors.text,
                    fontWeight = FontWeight.Bold,
                    textAlign  = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text      = "Название, описание, команды — всё в одном месте. Деплой на ваш webhook.",
                    style     = KotoTheme.typography.bodyMedium,
                    color     = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(18.dp))

            // Name / handle / tagline form
            FormCard(title = "ОСНОВНОЕ") {
                Field(label = "Имя", value = name, placeholder = "Pawket", onChange = { name = it })
                DividerLine()
                Field(label = "Handle", value = handle, placeholder = "@pawket", onChange = { handle = it.replace(" ", "").lowercase() })
                DividerLine()
                Field(label = "Описание", value = tagline, placeholder = "Кот-ассистент с ИИ", onChange = { tagline = it }, multiline = true)
            }

            FormCard(title = "КАТЕГОРИЯ") {
                val cats = listOf("AI · ассистенты", "Покупки", "Игры", "Финансы", "Утилиты", "Продуктивность")
                cats.forEachIndexed { i, c ->
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication        = ripple(bounded = true),
                                onClick           = { category = c },
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(text = c, style = KotoTheme.typography.bodyLarge, color = colors.text)
                        if (c == category) {
                            Icon(imageVector = KotoIcons.Check, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (i < cats.lastIndex) DividerLine()
                }
            }

            FormCard(title = "ДАЛЬШЕ") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(14.dp)) {
                    PendingStep("Команды и webhook", "Шаг 2")
                    PendingStep("Иконка и цвет", "Шаг 3")
                    PendingStep("Preview и деплой", "Шаг 4")
                }
            }

            Spacer(Modifier.height(18.dp))

            // Submit
            val enabled = name.isNotBlank() && handle.length >= 3
            Box(
                modifier         = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (enabled) colors.accent else colors.accent.copy(alpha = 0.4f))
                        .clickable(
                            enabled           = enabled,
                            interactionSource = remember { MutableInteractionSource() },
                            indication        = ripple(bounded = true),
                            onClick           = onDone,
                        )
                        .padding(vertical = 14.dp),
                    contentAlignment  = Alignment.Center,
                ) {
                    Text(
                        text       = "Продолжить",
                        style      = KotoTheme.typography.titleSmall,
                        color      = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun FormCard(title: String, content: @Composable () -> Unit) {
    val colors = KotoTheme.colors
    Text(
        text       = title,
        style      = KotoTheme.typography.labelMedium,
        color      = colors.textSecondary,
        fontWeight = FontWeight.SemiBold,
        modifier   = Modifier.padding(start = 24.dp, top = 14.dp, bottom = 6.dp),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface),
    ) {
        content()
    }
}

@Composable
private fun Field(
    label       : String,
    value       : String,
    placeholder : String,
    onChange    : (String) -> Unit,
    multiline   : Boolean = false,
) {
    val colors = KotoTheme.colors
    Row(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text     = label,
            style    = KotoTheme.typography.bodyMedium,
            color    = colors.textSecondary,
            modifier = Modifier.width(80.dp),
        )
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(text = placeholder, style = KotoTheme.typography.bodyLarge, color = colors.textTertiary)
            }
            BasicTextField(
                value         = value,
                onValueChange = onChange,
                singleLine    = !multiline,
                maxLines      = if (multiline) 4 else 1,
                textStyle     = LocalTextStyle.current.merge(
                    KotoTheme.typography.bodyLarge.copy(color = colors.text),
                ),
                cursorBrush   = SolidColor(colors.accent),
                modifier      = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DividerLine() {
    Box(modifier = Modifier
        .padding(start = 14.dp)
        .fillMaxWidth()
        .height(0.5.dp)
        .background(KotoTheme.colors.separator),
    )
}

@Composable
private fun PendingStep(title: String, step: String) {
    val colors = KotoTheme.colors
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(0.5.dp, colors.separator, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = title, style = KotoTheme.typography.bodyMedium, color = colors.text)
        Text(text = step, style = KotoTheme.typography.labelMedium, color = colors.textSecondary)
    }
}
