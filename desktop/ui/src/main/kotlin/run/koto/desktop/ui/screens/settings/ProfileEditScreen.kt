package run.koto.desktop.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import run.koto.desktop.ui.components.atoms.Avatar
import run.koto.desktop.ui.components.atoms.avatarColorFor
import run.koto.desktop.ui.icons.KotoIcons
import run.koto.desktop.ui.theme.KotoTheme
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * Profile editor — Discord-inspired layout, but tightened up for desktop.
 *
 *   - 170 dp banner painted with a 3-stop gradient based on the user's accent
 *     hue, fading into the surface (not pure black) so it lives in the page
 *     rather than punching a hole in it.
 *   - 140 dp avatar with a 5 dp ring matching the page background — the same
 *     trick Discord uses to make the circle "pop" out of the banner without a
 *     hard outline.
 *   - All form content max-width 640 dp and centered, the breakpoint at which
 *     readability stops improving — wider screens just get equal padding on
 *     both sides instead of impossibly long input fields.
 *   - Field labels are titleSmall + SemiBold (not the cramped uppercase
 *     labelMedium) — same scale as iOS Settings groups, easier to scan.
 *   - Save bar slides up with a subtle shadow when there are unsaved changes;
 *     it sits in a backdrop strip so the content scrolling behind it doesn't
 *     bleed visually.
 *
 * The banner needs to bleed edge-to-edge, so this screen renders its own top
 * bar (Назад + title) rather than living inside SettingsSubScreen's padded
 * wrapper — same pattern SafetyDetailScreen uses.
 */
@Composable
fun ProfileEditScreen(
    viewModel : ProfileEditViewModel,
    onBack    : () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val colors = KotoTheme.colors
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(Unit) { viewModel.load() }
    DisposableEffect(Unit) { onDispose { viewModel.close() } }

    var copyTick by remember { mutableStateOf(0) }
    var idCopied by remember { mutableStateOf(false) }
    LaunchedEffect(copyTick) {
        if (copyTick > 0) {
            idCopied = true
            delay(1500)
            idCopied = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        if (state.loading) {
            Column(modifier = Modifier.fillMaxSize()) {
                ProfileTopBar(onBack = onBack)
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingBlock()
                }
            }
            return@Box
        }

        // Banner is full-width edge-to-edge; the form content below is
        // centered and max-width 640 dp.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = if (state.dirty) 96.dp else 24.dp),
        ) {
            ProfileTopBar(onBack = onBack)
            BannerWithAvatar(
                accentSeed     = state.accountId,
                avatarUrl      = state.draftAvatar,
                avatarBytes    = state.draftAvatarBytes,
                bannerUrl      = state.draftBanner,
                bannerBytes    = state.draftBannerBytes,
                displayName    = state.draftName,
                avatarStatus   = state.avatar,
                bannerStatus   = state.banner,
                onPickAvatar   = viewModel::uploadAvatar,
                onRemoveAvatar = viewModel::removeAvatar,
                onPickBanner   = viewModel::uploadBanner,
                onRemoveBanner = viewModel::removeBanner,
            )

            Box(
                modifier         = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = FORM_MAX_WIDTH_DP.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                ) {
                    Spacer(Modifier.height(28.dp))

                    FieldCard(
                        label = "Отображаемое имя",
                        hint  = "${state.draftName.length}/${ProfileEditViewModel.NAME_MAX}",
                    ) {
                        EditField(
                            value       = state.draftName,
                            placeholder = "Как вас будут видеть",
                            onChange    = viewModel::setName,
                            singleLine  = true,
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    FieldCard(
                        label = "О себе",
                        hint  = "${state.draftBio.length}/${ProfileEditViewModel.BIO_MAX}",
                    ) {
                        EditField(
                            value       = state.draftBio,
                            placeholder = "Пара строк, которые увидят собеседники",
                            onChange    = viewModel::setBio,
                            singleLine  = false,
                            minLines    = 3,
                        )
                    }

                    Spacer(Modifier.height(28.dp))

                    SectionLabel("Идентификация")
                    Spacer(Modifier.height(8.dp))
                    MetaCard(
                        kotoId    = state.accountId,
                        username  = state.username,
                        idCopied  = idCopied,
                        onCopyId  = {
                            clipboard.setText(AnnotatedString(state.accountId))
                            copyTick++
                        },
                    )

                    Spacer(Modifier.height(28.dp))

                    SectionLabel("Так вас увидят другие")
                    Spacer(Modifier.height(8.dp))
                    PreviewCard(
                        accentSeed   = state.accountId,
                        displayName  = state.draftName,
                        bio          = state.draftBio,
                        username     = state.username,
                        avatarRender = state.draftAvatarBytes,
                        bannerRender = state.draftBannerBytes,
                    )

                    Spacer(Modifier.height(40.dp))
                }
            }
        }

        AnimatedVisibility(
            visible = state.dirty,
            enter   = slideInVertically(tween(220)) { it } + fadeIn(tween(220)),
            exit    = slideOutVertically(tween(180)) { it } + fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            SaveBarBackdrop {
                SaveBar(
                    status   = state.save,
                    onCancel = viewModel::discard,
                    onSave   = viewModel::save,
                )
            }
        }
    }
}

private const val FORM_MAX_WIDTH_DP = 640

// ────────────────────────────────────────────────────────────────────────────
//  Top bar
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileTopBar(onBack: () -> Unit) {
    val colors = KotoTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background),
    ) {
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
                Text(text = "Настройки", style = KotoTheme.typography.titleMedium, color = colors.accent)
            }
            Text(
                text       = "Профиль",
                style      = KotoTheme.typography.titleLarge,
                color      = colors.text,
                fontWeight = FontWeight.SemiBold,
                modifier   = Modifier.weight(1f),
                textAlign  = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.size(110.dp))
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
//  Banner + avatar
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun BannerWithAvatar(
    accentSeed     : String,
    avatarUrl      : String?,
    avatarBytes    : ByteArray?,
    bannerUrl      : String?,
    bannerBytes    : ByteArray?,
    displayName    : String,
    avatarStatus   : ProfileEditViewModel.AvatarStatus,
    bannerStatus   : ProfileEditViewModel.BannerStatus,
    onPickAvatar   : (ByteArray) -> Unit,
    onRemoveAvatar : () -> Unit,
    onPickBanner   : (ByteArray) -> Unit,
    onRemoveBanner : () -> Unit,
) {
    val colors = KotoTheme.colors
    val accent = avatarColorFor(accentSeed)
    var avatarMenuOpen by remember { mutableStateOf(false) }
    var bannerMenuOpen by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        // Banner: 3-stop gradient (or the user's banner image when set);
        // clickable to open the upload menu.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BANNER_HEIGHT_DP.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = ripple(bounded = true),
                    onClick           = { bannerMenuOpen = true },
                )
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            accent.copy(alpha = 1f),
                            accent.copy(alpha = 0.78f),
                            colors.surface,
                        ),
                    ),
                ),
        ) {
            // Real banner image bytes painted on top of the gradient.
            if (bannerBytes != null) {
                coil3.compose.AsyncImage(
                    model              = bannerBytes,
                    contentDescription = null,
                    contentScale       = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier           = Modifier.matchParentSize(),
                )
            }

            // "Изменить обложку" pill in the top-right corner — discoverable
            // hint that the banner is editable, like Discord's hover overlay.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication        = ripple(bounded = true),
                        onClick           = { bannerMenuOpen = true },
                    )
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector        = KotoIcons.Camera,
                        contentDescription = null,
                        tint               = Color.White,
                        modifier           = Modifier.size(14.dp),
                    )
                    Text(
                        text       = if (bannerUrl != null) "Изменить обложку" else "Загрузить обложку",
                        style      = KotoTheme.typography.labelMedium,
                        color      = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (bannerStatus is ProfileEditViewModel.BannerStatus.Uploading) {
                Box(
                    modifier         = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text       = "загрузка обложки…",
                        color      = Color.White,
                        style      = KotoTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        // Avatar overlapping the banner — half-bleed onto the form area below.
        Box(
            modifier = Modifier
                .padding(start = 32.dp)
                .padding(top = (BANNER_HEIGHT_DP - AVATAR_SIZE_DP / 2 - 8).dp)
                .size(AVATAR_SIZE_DP.dp),
        ) {
            // Background-coloured ring so the avatar reads as separated from
            // the banner without an ugly hard stroke.
            Box(
                modifier         = Modifier
                    .matchParentSize()
                    .clip(CircleShape)
                    .background(colors.background)
                    .padding(AVATAR_RING_DP.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication        = ripple(bounded = true),
                        onClick           = { avatarMenuOpen = true },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (avatarBytes != null) {
                    coil3.compose.AsyncImage(
                        model              = avatarBytes,
                        contentDescription = null,
                        contentScale       = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier           = Modifier
                            .size((AVATAR_SIZE_DP - AVATAR_RING_DP * 2).dp)
                            .clip(CircleShape),
                    )
                } else {
                    Avatar(
                        initials = initialsOf(displayName),
                        color    = accent,
                        size     = (AVATAR_SIZE_DP - AVATAR_RING_DP * 2).dp,
                    )
                }
                if (avatarStatus is ProfileEditViewModel.AvatarStatus.Uploading) {
                    Box(
                        modifier         = Modifier
                            .matchParentSize()
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text       = "загрузка…",
                            color      = Color.White,
                            style      = KotoTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            // Camera badge — bottom-right corner, accent-coloured for visibility.
            Box(
                modifier         = Modifier
                    .align(Alignment.BottomEnd)
                    .size(38.dp)
                    .shadow(4.dp, CircleShape, clip = false)
                    .clip(CircleShape)
                    .background(colors.accent)
                    .border(3.dp, colors.background, CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication        = ripple(bounded = false, radius = 22.dp),
                        onClick           = { avatarMenuOpen = true },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector        = KotoIcons.Camera,
                    contentDescription = "Изменить аватар",
                    tint               = Color.White,
                    modifier           = Modifier.size(18.dp),
                )
            }
        }

        // Stack failure messages so both can show simultaneously without
        // overlapping. Banner failure is more prominent (top of banner).
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 32.dp, bottom = 6.dp),
        ) {
            if (avatarStatus is ProfileEditViewModel.AvatarStatus.Failed) {
                Text(
                    text  = "Аватар: ${avatarStatus.message}",
                    style = KotoTheme.typography.bodySmall,
                    color = colors.error,
                )
            }
            if (bannerStatus is ProfileEditViewModel.BannerStatus.Failed) {
                Text(
                    text  = "Обложка: ${bannerStatus.message}",
                    style = KotoTheme.typography.bodySmall,
                    color = colors.error,
                )
            }
        }
    }

    if (avatarMenuOpen) {
        ImageMenu(
            title     = "Фото профиля",
            hasImage  = avatarUrl != null,
            sizeHint  = "Квадрат · до 8 МБ · PNG/JPEG/WebP",
            onPick    = { bytes ->
                avatarMenuOpen = false
                onPickAvatar(bytes)
            },
            onRemove  = {
                avatarMenuOpen = false
                onRemoveAvatar()
            },
            onDismiss = { avatarMenuOpen = false },
        )
    }
    if (bannerMenuOpen) {
        ImageMenu(
            title     = "Обложка профиля",
            hasImage  = bannerUrl != null,
            sizeHint  = "3:1 · до 12 МБ · PNG/JPEG/WebP",
            onPick    = { bytes ->
                bannerMenuOpen = false
                onPickBanner(bytes)
            },
            onRemove  = {
                bannerMenuOpen = false
                onRemoveBanner()
            },
            onDismiss = { bannerMenuOpen = false },
        )
    }
}

private const val BANNER_HEIGHT_DP = 170
private const val AVATAR_SIZE_DP   = 140
private const val AVATAR_RING_DP   = 5

@Composable
private fun ImageMenu(
    title     : String,
    hasImage  : Boolean,
    sizeHint  : String,
    onPick    : (ByteArray) -> Unit,
    onRemove  : () -> Unit,
    onDismiss : () -> Unit,
) {
    val colors = KotoTheme.colors
    val scope  = rememberCoroutineScope()
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(min = 300.dp, max = 380.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surface)
                .padding(8.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(text = title, style = KotoTheme.typography.titleMedium, color = colors.text, fontWeight = FontWeight.SemiBold)
                Text(
                    text     = sizeHint,
                    style    = KotoTheme.typography.bodySmall,
                    color    = colors.textSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            MenuItem(
                label   = "Загрузить с компьютера",
                color   = colors.text,
                onClick = {
                    scope.launch {
                        val bytes = pickImageBytes()
                        if (bytes != null) onPick(bytes) else onDismiss()
                    }
                },
            )
            if (hasImage) {
                MenuItem(
                    label   = "Удалить",
                    color   = colors.error,
                    onClick = onRemove,
                )
            }
            Box(modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .height(0.5.dp)
                .background(colors.separator),
            )
            MenuItem(
                label   = "Отмена",
                color   = colors.textSecondary,
                onClick = onDismiss,
            )
        }
    }
}

@Composable
private fun MenuItem(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier         = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = ripple(bounded = true),
                onClick           = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text = label, style = KotoTheme.typography.bodyLarge, color = color, fontWeight = FontWeight.Medium)
    }
}


// ────────────────────────────────────────────────────────────────────────────
//  Editable fields
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text       = text,
        style      = KotoTheme.typography.titleSmall,
        color      = KotoTheme.colors.textSecondary,
        fontWeight = FontWeight.SemiBold,
        modifier   = Modifier.padding(start = 4.dp),
    )
}

@Composable
private fun FieldCard(
    label   : String,
    hint    : String?,
    content : @Composable () -> Unit,
) {
    val colors = KotoTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(
                text       = label,
                style      = KotoTheme.typography.titleSmall,
                color      = colors.textSecondary,
                fontWeight = FontWeight.SemiBold,
                modifier   = Modifier.weight(1f),
            )
            if (hint != null) {
                Text(text = hint, style = KotoTheme.typography.labelMedium, color = colors.textTertiary)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun EditField(
    value       : String,
    placeholder : String,
    onChange    : (String) -> Unit,
    singleLine  : Boolean,
    minLines    : Int = 1,
) {
    val colors = KotoTheme.colors
    Box(modifier = Modifier.fillMaxWidth()) {
        if (value.isEmpty()) {
            Text(
                text   = placeholder,
                style  = KotoTheme.typography.bodyLarge,
                color  = colors.textTertiary,
            )
        }
        BasicTextField(
            value         = value,
            onValueChange = onChange,
            singleLine    = singleLine,
            minLines      = minLines,
            cursorBrush   = SolidColor(colors.accent),
            textStyle     = LocalTextStyle.current.merge(
                KotoTheme.typography.bodyLarge.copy(color = colors.text, fontWeight = FontWeight.Normal),
            ),
            modifier      = Modifier.fillMaxWidth(),
        )
    }
}

// ────────────────────────────────────────────────────────────────────────────
//  Meta row
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun MetaCard(
    kotoId    : String,
    username  : String?,
    idCopied  : Boolean,
    onCopyId  : () -> Unit,
) {
    val colors = KotoTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface),
    ) {
        MetaRow(
            label    = "Koto ID",
            value    = if (idCopied) "скопировано ✓" else (if (kotoId.length > 16) "${kotoId.take(8)}…${kotoId.takeLast(4)}" else kotoId),
            valueClr = if (idCopied) colors.accent else colors.textSecondary,
            trail    = MetaTrail.Copy,
            onClick  = onCopyId,
        )
        Box(modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(0.5.dp)
            .background(colors.separator),
        )
        MetaRow(
            label    = "Имя пользователя",
            value    = username?.let { "@$it" } ?: "не задано",
            valueClr = colors.textSecondary,
            trail    = MetaTrail.None,
            onClick  = null,
        )
    }
}

private enum class MetaTrail { None, Copy }

@Composable
private fun MetaRow(
    label    : String,
    value    : String,
    valueClr : Color,
    trail    : MetaTrail,
    onClick  : (() -> Unit)?,
) {
    val colors = KotoTheme.colors
    val mod = if (onClick != null) {
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = ripple(bounded = true),
                onClick           = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    } else {
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)
    }

    Row(modifier = mod, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text       = label,
            style      = KotoTheme.typography.bodyLarge,
            color      = colors.text,
            modifier   = Modifier.weight(1f),
        )
        Text(
            text       = value,
            style      = KotoTheme.typography.bodyMedium,
            color      = valueClr,
            fontWeight = FontWeight.Medium,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
        )
        if (trail == MetaTrail.Copy) {
            Icon(
                imageVector        = KotoIcons.Copy,
                contentDescription = null,
                tint               = colors.textTertiary,
                modifier           = Modifier.size(16.dp),
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
//  Live preview card
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun PreviewCard(
    accentSeed   : String,
    displayName  : String,
    bio          : String,
    username     : String?,
    avatarRender : Any?, // ByteArray | String (URL) | null
    bannerRender : Any?,
) {
    val colors = KotoTheme.colors
    val accent = avatarColorFor(accentSeed)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface),
    ) {
        Column {
            // Mini banner — same gradient as the real one but compressed.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(accent.copy(alpha = 1f), accent.copy(alpha = 0.7f)),
                        ),
                    ),
            ) {
                if (bannerRender != null) {
                    coil3.compose.AsyncImage(
                        model              = bannerRender,
                        contentDescription = null,
                        contentScale       = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier           = Modifier.matchParentSize(),
                    )
                }
            }
            // Avatar overlapping the mini-banner. Outer Box is sized so the
            // avatar's ring extends above its parent without affecting layout.
            Row(
                modifier              = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp).padding(top = (-32).dp.let { it } /* visual lift */ * 0),
                verticalAlignment     = Alignment.Top,
            ) {
                // We use negative offset via padding-trick: place avatar with top
                // offset = -36 dp via Box layoutid tricks would be cleaner, but a
                // simple Spacer + Modifier.padding(top = (-36).dp) on inner Box
                // does the lift here.
                Box(
                    modifier         = Modifier
                        .padding(top = 0.dp)
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(colors.surface)
                        .padding(3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (avatarRender != null) {
                        coil3.compose.AsyncImage(
                            model              = avatarRender,
                            contentDescription = null,
                            contentScale       = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier           = Modifier
                                .size(66.dp)
                                .clip(CircleShape),
                        )
                    } else {
                        Avatar(initials = initialsOf(displayName), color = accent, size = 66.dp)
                    }
                }
                Column(
                    modifier = Modifier
                        .padding(start = 14.dp, top = 6.dp)
                        .weight(1f),
                ) {
                    Text(
                        text       = displayName.ifBlank { "Без имени" },
                        style      = KotoTheme.typography.titleLarge,
                        color      = colors.text,
                        fontWeight = FontWeight.Bold,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                    )
                    if (username != null) {
                        Text(
                            text       = "@$username",
                            style      = KotoTheme.typography.bodyMedium,
                            color      = colors.textSecondary,
                            modifier   = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
            if (bio.isNotBlank()) {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 18.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.background)
                    .padding(14.dp),
                ) {
                    Text(text = bio, style = KotoTheme.typography.bodyMedium, color = colors.text)
                }
            } else {
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
//  Save bar
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun SaveBarBackdrop(content: @Composable () -> Unit) {
    val colors = KotoTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to colors.background.copy(alpha = 0f),
                    0.5f to colors.background.copy(alpha = 0.85f),
                    1f to colors.background,
                ),
            )
            .padding(top = 24.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
    ) {
        Box(
            modifier         = Modifier.widthIn(max = (FORM_MAX_WIDTH_DP + 32).dp).fillMaxWidth().align(Alignment.BottomCenter),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun SaveBar(
    status   : ProfileEditViewModel.SaveStatus,
    onCancel : () -> Unit,
    onSave   : () -> Unit,
) {
    val colors = KotoTheme.colors
    val saving = status is ProfileEditViewModel.SaveStatus.Saving
    val failed = status as? ProfileEditViewModel.SaveStatus.Failed

    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(1.dp, colors.separator, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = "Несохранённые изменения",
                style      = KotoTheme.typography.titleSmall,
                color      = colors.text,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text     = failed?.message ?: "Сохраните, чтобы они стали видны другим.",
                style    = KotoTheme.typography.bodySmall,
                color    = if (failed != null) colors.error else colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        BarButton(label = "Отменить", primary = false, enabled = !saving, onClick = onCancel)
        BarButton(label = if (saving) "сохраняем…" else "Сохранить", primary = true, enabled = !saving, onClick = onSave)
    }
}

@Composable
private fun BarButton(label: String, primary: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = KotoTheme.colors
    val bg = when {
        primary && !enabled -> colors.accent.copy(alpha = 0.5f)
        primary             -> colors.accent
        else                -> colors.background
    }
    val fg = if (primary) Color.White else colors.text
    Box(
        modifier         = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(
                enabled           = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication        = ripple(bounded = true),
                onClick           = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = KotoTheme.typography.titleSmall, color = fg, fontWeight = FontWeight.SemiBold)
    }
}

// ────────────────────────────────────────────────────────────────────────────
//  Loading
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun LoadingBlock() {
    val colors = KotoTheme.colors
    Box(
        modifier         = Modifier
            .widthIn(max = 320.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "загружаем профиль…", style = KotoTheme.typography.bodyMedium, color = colors.textSecondary)
    }
}

private fun initialsOf(name: String): String =
    name.split(' ', limit = 3)
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifBlank { "?" }
