package run.koto.desktop.ui.screens.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import run.koto.desktop.ui.components.atoms.AnimatedIconButton
import run.koto.desktop.ui.components.atoms.Avatar
import run.koto.desktop.ui.icons.KotoIcons
import run.koto.desktop.ui.theme.KotoEasing
import run.koto.desktop.ui.theme.KotoTheme

@Composable
fun SettingsScreen(
    profileName     : String,
    profileKotoId   : String,
    profileUsername : String?,
    deviceCount     : Int?,
    privacyPreset   : run.koto.desktop.domain.model.PrivacyPreset,
    storageDetail   : String?,
    networkDetail   : String?,
    autoDownloadDetail : String?,
    onBack          : () -> Unit,
    onToggleTheme   : () -> Unit,
    onOpenSection   : (String) -> Unit,
    onSignOut       : () -> Unit,
) {
    val colors     = KotoTheme.colors
    val scope      = rememberCoroutineScope()
    val clipboard  = LocalClipboardManager.current
    val enterAlpha = remember { Animatable(0f) }
    val enterY     = remember { Animatable(12f) }
    LaunchedEffect(Unit) {
        scope.launch { enterAlpha.animateTo(1f, tween(280, easing = KotoEasing)) }
        scope.launch { enterY    .animateTo(0f, tween(280, easing = KotoEasing)) }
    }

    // "Мой Koto ID" copy feedback. Each click bumps [copyTick]; the
    // LaunchedEffect keyed off it shows "скопировано ✓" for 1.5s and resets
    // — restarts the timer if the user clicks again mid-flash.
    var copyTick    by remember { mutableStateOf(0) }
    var kotoIdCopied by remember { mutableStateOf(false) }
    LaunchedEffect(copyTick) {
        if (copyTick > 0) {
            kotoIdCopied = true
            delay(1500)
            kotoIdCopied = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Row(
            modifier              = Modifier
                .align(Alignment.TopEnd)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ChromeIcon(
                glyph   = if (colors.isLight) "◐" else "☀",
                onClick = onToggleTheme,
            )
            AnimatedIconButton(
                icon               = KotoIcons.Close,
                onClick            = onBack,
                size               = 34.dp,
                iconSize           = 16.dp,
                contentDescription = "Закрыть настройки",
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .graphicsLayer { alpha = enterAlpha.value; translationY = enterY.value },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))

            Box(
                modifier         = Modifier.widthIn(max = SETTINGS_MAX_WIDTH).fillMaxWidth(),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text       = "Настройки",
                    style      = KotoTheme.typography.displayMedium,
                    color      = colors.text,
                    fontWeight = FontWeight.ExtraBold,
                    modifier   = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 18.dp),
                )
            }

            ProfileCard(
                name    = profileName,
                kotoId  = profileKotoId,
                onClick = { onOpenSection("profile") },
            )

            SettingsSection(label = "АККАУНТ") {
                SettingsRow(
                    ic          = IcTile(KotoIcons.Qr, Color(0xFF7C5CFF)),
                    title       = "Мой Koto ID",
                    detail      = if (kotoIdCopied) "скопировано ✓" else "скопировать",
                    detailColor = if (kotoIdCopied) colors.accent else null,
                    onClick     = {
                        clipboard.setText(AnnotatedString(profileKotoId))
                        copyTick++
                    },
                )
                SettingsRow(ic = IcTile(KotoIcons.Copy,        Color(0xFFFF6B35)), title = "Фраза восстановления", detail = "12 слов",          onClick = { onOpenSection("seed") })
                SettingsRow(
                    ic      = IcTile(KotoIcons.User, Color(0xFF00A676)),
                    title   = "Связанные устройства",
                    detail  = deviceCount?.toString() ?: "…",
                    onClick = { onOpenSection("devices") },
                )
                SettingsRow(
                    ic      = IcTile(KotoIcons.User, Color(0xFF3276FF)),
                    title   = "Имя пользователя",
                    detail  = profileUsername?.let { "@$it" } ?: "не задано",
                    onClick = { onOpenSection("username") },
                    isLast  = true,
                )
            }

            SettingsSection(label = "ПРИВАТНОСТЬ") {
                SettingsRow(
                    ic      = IcTile(KotoIcons.Shield, Color(0xFF00A676)),
                    title   = "Приватность",
                    detail  = privacyPreset.russianLabel(),
                    onClick = { onOpenSection("privacy") },
                )
                SettingsRow(ic = IcTile(KotoIcons.Timer,       Color(0xFFFF6B35)), title = "Исчезающие сообщения", detail = "выкл",             onClick = { onOpenSection("ephemeral") })
                SettingsRow(ic = IcTile(KotoIcons.Lock,        Color(0xFF7C5CFF)), title = "Блокировка экрана",    detail = "Face ID",          onClick = { onOpenSection("screenlock") })
                SettingsRow(ic = IcTile(KotoIcons.CheckCircle, Color(0xFF13B3A5)), title = "Проверка безопасности",                             onClick = { onOpenSection("safety") })
                SettingsRow(ic = IcTile(KotoIcons.Shield,      Color(0xFF3276FF)), title = "Закрытые отправители", detail = "вкл",              onClick = { onOpenSection("sealed") }, isLast = true)
            }

            SettingsSection(label = "ДАННЫЕ И ХРАНИЛИЩЕ") {
                SettingsRow(ic = IcTile(KotoIcons.Archive, Color(0xFFF0B400)), title = "Хранилище",          detail = storageDetail      ?: "…", onClick = { onOpenSection("storage") })
                SettingsRow(ic = IcTile(KotoIcons.Globe,   Color(0xFF3276FF)), title = "Использование сети", detail = networkDetail      ?: "…", onClick = { onOpenSection("network") })
                SettingsRow(ic = IcTile(KotoIcons.Image,   Color(0xFFFF7A9A)), title = "Автозагрузка",       detail = autoDownloadDetail ?: "…", onClick = { onOpenSection("auto") }, isLast = true)
            }

            SettingsSection(label = "ВНЕШНИЙ ВИД") {
                SettingsRow(ic = IcTile(KotoIcons.Face,        Color(0xFF7C5CFF)), title = "Тема и цвет",          detail = if (colors.isLight) "Светлая" else "Тёмная", onClick = { onOpenSection("theme") })
                SettingsRow(ic = IcTile(KotoIcons.Document,    Color(0xFF00A676)), title = "Размер шрифта",        detail = "обычный",          onClick = { onOpenSection("font") })
                SettingsRow(ic = IcTile(KotoIcons.Image,       Color(0xFFFF6B35)), title = "Обои чатов",           detail = "Koto · оранжевый", onClick = { onOpenSection("wallpaper") }, isLast = true)
            }

            SettingsSection(label = "УВЕДОМЛЕНИЯ И ЗВОНКИ") {
                SettingsRow(ic = IcTile(KotoIcons.Bell,        Color(0xFFE74C6F)), title = "Уведомления",          detail = "вкл",              onClick = { onOpenSection("notifications") })
                SettingsRow(ic = IcTile(KotoIcons.Phone,       Color(0xFF00A676)), title = "Звонки",               detail = "Koto + iOS",       onClick = { onOpenSection("calls") }, isLast = true)
            }

            SettingsSection(label = "ПОДДЕРЖКА") {
                SettingsRow(ic = IcTile(KotoIcons.Alert,       Color(0xFFF0B400)), title = "Справка",                                           onClick = { onOpenSection("help") })
                SettingsRow(ic = IcTile(KotoIcons.DotCircle,   Color(0xFF7C5CFF)), title = "О Koto",               detail = "26.4.1",           onClick = { onOpenSection("about") }, isLast = true)
            }

            SettingsSection(label = null) {
                SettingsRow(
                    ic      = IcTile(KotoIcons.Close, Color(0xFFFF3B30)),
                    title   = "Выйти из Koto ID",
                    danger  = true,
                    isLast  = true,
                    onClick = onSignOut,
                )
            }

            Text(
                text      = "Koto 26.4.1 · защита от края до края",
                style     = KotoTheme.typography.labelMedium,
                color     = colors.textSecondary,
                modifier  = Modifier
                    .widthIn(max = SETTINGS_MAX_WIDTH)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

private val SETTINGS_MAX_WIDTH = 620.dp

@Composable
private fun ChromeIcon(
    icon    : ImageVector? = null,
    glyph   : String?      = null,
    onClick : () -> Unit,
) {
    val colors      = KotoTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    Box(
        modifier          = Modifier
            .size(34.dp)
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication        = null,
                onClick           = onClick,
            )
            .clip(CircleShape)
            .background(if (hovered) colors.surface else Color.Transparent),
        contentAlignment  = Alignment.Center,
    ) {
        when {
            icon  != null -> Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = colors.textSecondary,
                modifier           = Modifier.size(16.dp),
            )
            glyph != null -> Text(
                text  = glyph,
                style = KotoTheme.typography.titleLarge,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun ProfileCard(name: String, kotoId: String, onClick: () -> Unit) {
    val colors      = KotoTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg = if (hovered) colors.elevated else colors.surface

    Row(
        modifier              = Modifier
            .widthIn(max = SETTINGS_MAX_WIDTH)
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication        = ripple(bounded = true),
                onClick           = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Avatar(
            initials = (name.firstOrNull()?.uppercase() ?: "Я"),
            color    = colors.accent,
            size     = 60.dp,
        )
        Column(modifier = Modifier.widthIn(min = 0.dp)) {
            Text(
                text       = name,
                style      = KotoTheme.typography.titleLarge,
                color      = colors.text,
                fontWeight = FontWeight.Bold,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
            Text(
                text     = kotoId.take(16) + "…",
                style    = KotoTheme.typography.monoSmall,
                color    = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier              = Modifier.padding(top = 6.dp),
            ) {
                Icon(
                    imageVector        = KotoIcons.Lock,
                    contentDescription = null,
                    tint               = colors.accent,
                    modifier           = Modifier.size(10.dp),
                )
                Text(text = "ключи на устройстве", style = KotoTheme.typography.labelMedium, color = colors.accent)
            }
        }
    }
}

@Composable
private fun SettingsSection(
    label   : String?,
    content : @Composable () -> Unit,
) {
    val colors = KotoTheme.colors
    Column(
        modifier            = Modifier
            .widthIn(max = SETTINGS_MAX_WIDTH)
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 18.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        if (label != null) {
            Text(
                text       = label,
                style      = KotoTheme.typography.labelMedium,
                color      = colors.textSecondary,
                fontWeight = FontWeight.SemiBold,
                modifier   = Modifier.padding(start = 28.dp, end = 24.dp, bottom = 6.dp),
            )
        }
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
}

@Composable
private fun SettingsRow(
    ic          : IcTile,
    title       : String,
    detail      : String? = null,
    detailColor : Color?  = null,
    isLast      : Boolean = false,
    danger      : Boolean = false,
    onClick     : () -> Unit,
) {
    val colors      = KotoTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val hoverBg = if (hovered) colors.elevated.copy(alpha = 0.5f) else Color.Transparent

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .background(hoverBg)
                .hoverable(interaction)
                .clickable(
                    interactionSource = interaction,
                    indication        = ripple(bounded = true),
                    onClick           = onClick,
                )
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconTile(ic = ic)
            Text(
                text       = title,
                style      = KotoTheme.typography.bodyLarge,
                color      = if (danger) colors.error else colors.text,
                fontWeight = FontWeight.Medium,
                modifier   = Modifier.weight(1f),
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
            if (detail != null) {
                Text(
                    text  = detail,
                    style = KotoTheme.typography.bodyMedium,
                    color = detailColor ?: colors.textSecondary,
                )
            }
            if (!danger) {
                Icon(
                    imageVector        = KotoIcons.ChevronRight,
                    contentDescription = null,
                    tint               = colors.textTertiary,
                    modifier           = Modifier.size(16.dp),
                )
            }
        }
        if (!isLast) {
            Box(modifier = Modifier
                .fillMaxWidth()
                .padding(start = 60.dp)
                .height(0.5.dp)
                .background(colors.separator),
            )
        }
    }
}

@Composable
private fun IconTile(ic: IcTile) {
    Box(
        modifier          = Modifier
            .size(30.dp)
            .clip(KotoTheme.shapes.iconTile)
            .background(ic.color),
        contentAlignment  = Alignment.Center,
    ) {
        Icon(
            imageVector        = ic.icon,
            contentDescription = null,
            tint               = Color.White,
            modifier           = Modifier.size(18.dp),
        )
    }
}

/** Colored tile descriptor — icon glyph + background color. */
internal data class IcTile(val icon: ImageVector, val color: Color)
