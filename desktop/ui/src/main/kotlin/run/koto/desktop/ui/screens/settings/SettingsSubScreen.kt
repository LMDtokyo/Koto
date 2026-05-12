package run.koto.desktop.ui.screens.settings

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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import run.koto.desktop.ui.icons.KotoIcons
import run.koto.desktop.ui.theme.KotoTheme

/**
 * Generic Settings sub-screen — shared top-bar + scrollable body. Caller passes
 * the section title and a content lambda; no per-section screens duplicate the
 * chrome.
 *
 * Real per-section content comes in Phase 4 when each bucket (Privacy, Storage,
 * Theme, Notifications, Calls) gets its own full form. For now this serves as
 * a visually complete scaffold that routes from the main Settings tree.
 */
@Composable
fun SettingsSubScreen(
    title   : String,
    onBack  : () -> Unit,
    content : @Composable () -> Unit,
) {
    val colors = KotoTheme.colors

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 14.dp),
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
                    Text(text = "Настройки", style = KotoTheme.typography.titleMedium, color = colors.accent)
                }
                Text(
                    text       = title,
                    style      = KotoTheme.typography.titleLarge,
                    color      = colors.text,
                    fontWeight = FontWeight.SemiBold,
                    textAlign  = TextAlign.Center,
                    modifier   = Modifier.weight(1f),
                )
                Spacer(Modifier.size(96.dp))
            }
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(colors.separator))
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                content()
            }
        }
    }
}

/** Default content for a generic sub-section — a placeholder copy block, used
 *  until each section's full form lands. Grouped so the app still looks
 *  comprehensive when users click through.  */
@Composable
fun SettingsSubPlaceholder(sectionKey: String) {
    val colors = KotoTheme.colors
    val copy = when (sectionKey) {
        "profile"       -> "Имя, аватар, биография и видимость вашей учётки."
        "kotoid"        -> "Полный Koto ID, QR-код и кнопка копирования."
        "seed"          -> "Показ 13-словной фразы восстановления — требует подтверждения."
        "devices"       -> "Список привязанных устройств, последний вход и отзыв сессий."
        "username"      -> "Публичный тег @username, по которому вас могут найти."
        "privacy"       -> "Кто видит ваш онлайн, фото профиля, последнее был в сети."
        "ephemeral"     -> "Таймер автоудаления сообщений: выкл / 1ч / 1д / 1нед."
        "screenlock"    -> "Face ID / Touch ID / PIN для входа в приложение."
        "safety"        -> "Проверка безопасности: сверка ключей с собеседником."
        "sealed"        -> "Закрытые отправители — не раскрывать, от кого идут сообщения."
        "storage"       -> "Управление кэшем медиа, голосовыми, документами."
        "network"       -> "Учёт трафика, использование за месяц, соединения."
        "auto"          -> "Автозагрузка по Wi-Fi / моб. сети / роуминге."
        "theme"         -> "Светлая / тёмная / авто. Акцентный цвет."
        "font"          -> "Динамический размер шрифта и высота строки."
        "wallpaper"     -> "Фон чатов: Koto-оранжевый, паттерны, свои изображения."
        "notifications" -> "Звук, вибро, превью — в чатах и на блокировке."
        "calls"         -> "Интеграция с CallKit (iOS) и Koto-звонки на устройстве."
        "help"          -> "FAQ, контакт поддержки, отчёт о проблеме."
        "about"         -> "Версия, сборка, лицензии, крипто-примитивы."
        else            -> "Раздел в разработке — заполним позже."
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .padding(20.dp),
    ) {
        Text(
            text  = copy,
            style = KotoTheme.typography.bodyLarge,
            color = colors.text,
        )
    }
}
