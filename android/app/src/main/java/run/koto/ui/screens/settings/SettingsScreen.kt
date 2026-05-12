package run.koto.ui.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import run.koto.ui.components.KotoSwitch
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import run.koto.ui.theme.*
import java.io.File

// ─── Design tokens — NOW DYNAMIC via KotoTheme ───────────────────────────────
// All colors pulled from KotoTheme.colors so they respond to dark/light toggle.

// ─── Card modifier (uses runtime colors) ─────────────────────────────────────

@Composable
private fun Modifier.settingsCard(
    cardBg     : Color = KotoTheme.colors.surface,
    cardBorder : Color = KotoTheme.colors.divider,
    topGlow    : Color = KotoTheme.colors.onSurface.copy(alpha = 0.06f),
    radius     : Float = 48f,
): Modifier = this
    .clip(RoundedCornerShape(16.dp))
    .background(cardBg)
    .border(0.5.dp, cardBorder, RoundedCornerShape(16.dp))
    .drawBehind {
        drawLine(
            color       = topGlow,
            start       = Offset(radius, 0f),
            end         = Offset(size.width - radius, 0f),
            strokeWidth = 1f,
        )
    }

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun SettingsScreen(
    viewModel : SettingsViewModel = hiltViewModel(),
    onBack    : () -> Unit,
) {
    val state     by viewModel.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    val scope     = rememberCoroutineScope()

    // Edit-display-name dialog state
    var showEditNameDialog by remember { mutableStateOf(false) }

    // ── Stretchy avatar overscroll ─────────────────────────────────────────────
    // Captures unconsumed scroll when the list is at the top.
    // During drag: snaps immediately so the scale tracks the finger.
    // On release : springs back with a medium-bouncy animation.
    val overscrollAnim = remember { Animatable(0f) }
    val overscrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // available.y > 0 means the list is at the top and the user is pulling down
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    scope.launch {
                        overscrollAnim.snapTo(
                            (overscrollAnim.value + available.y * 0.4f).coerceAtLeast(0f)
                        )
                    }
                }
                return Offset.Zero
            }
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                overscrollAnim.animateTo(
                    targetValue   = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness    = Spring.StiffnessMedium,
                    )
                )
                return Velocity.Zero
            }
        }
    }
    // Max 50% growth (avatar goes from 76dp to ~114dp at hard pull)
    val overscrollScale = 1f + (overscrollAnim.value / 350f).coerceIn(0f, 0.5f)

    // Show snackbar on avatar upload error
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(state.avatarError) {
        state.avatarError?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearAvatarError()
        }
    }

    Scaffold(
        containerColor = KotoTheme.colors.background,
        snackbarHost   = {
            // The floating bottom nav bar (~88dp incl. margin) sits on top of
            // the Scaffold, so the default snackbar position is hidden behind
            // it. Lift the snackbar up so it stays visible.
            SnackbarHost(
                hostState = snackbarHost,
                modifier  = Modifier.padding(bottom = 96.dp),
            )
        },
        topBar = {
            SettingsTopBar(onBack = onBack)
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().nestedScroll(overscrollConnection)) {
        if (state.isLoaded) LazyColumn(
            modifier            = Modifier.fillMaxSize().padding(padding),
            contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {

            // Security warning banner — only shown when device is compromised
            if (state.securityStatus.isCompromised) {
                item(key = "security_warning") {
                    SecurityWarningBanner(state.securityStatus)
                }
            }

            item(key = "profile") {
                ProfileCard(
                    accountId         = state.accountId,
                    displayName       = state.displayName,
                    avatarDisplayUrl  = state.avatarDisplayUrl,
                    isUploadingAvatar = state.isUploadingAvatar,
                    hasAvatar         = state.avatarFileId.isNotBlank(),
                    overscrollScale   = overscrollScale,
                    onCopyId          = { clipboard.setText(AnnotatedString(state.accountId)) },
                    onAvatarSelected  = viewModel::uploadAvatar,
                    onRemoveAvatar    = viewModel::removeAvatar,
                    onEditName        = { showEditNameDialog = true },
                )
            }

            item(key = "appearance") {
                Section(label = "ОФОРМЛЕНИЕ") {
                    SwitchRow(
                        icon      = Icons.Default.DarkMode,
                        iconTint  = Color(0xFF7B61FF),
                        title     = "Тёмная тема",
                        subtitle  = "Переключить оформление",
                        checked   = state.darkMode,
                        onToggle  = viewModel::toggleDarkMode,
                    )
                }
            }

            item(key = "privacy") {
                Section(label = "ПРИВАТНОСТЬ") {
                    SwitchRow(
                        icon      = Icons.Default.Lock,
                        iconTint  = Color(0xFF5E5CE6),
                        title     = "Блокировка экрана",
                        subtitle  = "Биометрия или PIN",
                        checked   = state.screenLock,
                        onToggle  = viewModel::toggleScreenLock,
                    )
                    RowDivider()
                    SwitchRow(
                        icon      = Icons.Default.Timer,
                        iconTint  = Color(0xFFFF9F0A),
                        title     = "Исчезающие сообщения",
                        subtitle  = "По умолчанию 7 дней",
                        checked   = state.disappearingMessages,
                        onToggle  = viewModel::toggleDisappearing,
                    )
                    RowDivider()
                    SwitchRow(
                        icon      = Icons.Default.VisibilityOff,
                        iconTint  = Color(0xFF64D2FF),
                        title     = "Скрыть в списке задач",
                        subtitle  = "Размываем снимок экрана",
                        checked   = state.hideFromRecents,
                        onToggle  = viewModel::toggleHideFromRecents,
                    )
                }
            }

            item(key = "notifications") {
                Section(label = "УВЕДОМЛЕНИЯ") {
                    SwitchRow(
                        icon      = Icons.Default.Notifications,
                        iconTint  = Color(0xFFFF453A),
                        title     = "Push-уведомления",
                        subtitle  = "UnifiedPush — без Google",
                        checked   = state.notificationsEnabled,
                        onToggle  = viewModel::toggleNotifications,
                    )
                }
            }

            item(key = "network") {
                Section(label = "СЕТЬ") {
                    SwitchRow(
                        icon      = Icons.Default.VpnKey,
                        iconTint  = if (state.proxyEnabled) Color(0xFF30D158) else Color(0xFF98989D),
                        title     = "Прокси",
                        subtitle  = state.proxyConfig?.displayLabel() ?: "Shadowsocks / VMess / SOCKS5",
                        checked   = state.proxyEnabled,
                        onToggle  = viewModel::toggleProxy,
                        badge     = if (state.proxyEnabled) "Активен" else null,
                    )
                    RowDivider()
                    NavRow(
                        icon     = Icons.Default.Link,
                        iconTint = Color(0xFF0A84FF),
                        title    = "Настроить прокси",
                        value    = if (state.proxyUri.isNotBlank()) "Задан" else "Не задан",
                        onClick  = viewModel::showProxyDialog,
                    )
                    RowDivider()
                    SwitchRow(
                        icon      = Icons.Default.Security,
                        iconTint  = when (state.torStatus) {
                            run.koto.network.TorStatus.CONNECTED  -> Color(0xFF30D158)
                            run.koto.network.TorStatus.CONNECTING -> Color(0xFFFFD60A)
                            run.koto.network.TorStatus.ERROR      -> Color(0xFFFF453A)
                            else                                  -> Color(0xFF98989D)
                        },
                        title     = "Tor",
                        subtitle  = when {
                            state.torMessage != null -> state.torMessage!!
                            state.torStatus == run.koto.network.TorStatus.CONNECTING -> "Подключение…"
                            state.torStatus == run.koto.network.TorStatus.CONNECTED  -> "Весь трафик через Tor"
                            else -> "Встроенный Tor (SOCKS5)"
                        },
                        checked   = state.torEnabled,
                        onToggle  = viewModel::toggleTor,
                        badge     = when (state.torStatus) {
                            run.koto.network.TorStatus.CONNECTED  -> "Подключён"
                            run.koto.network.TorStatus.CONNECTING -> "…"
                            run.koto.network.TorStatus.ERROR      -> "Ошибка"
                            else -> null
                        },
                        badgeColor = when (state.torStatus) {
                            run.koto.network.TorStatus.ERROR      -> Color(0xFFFF453A)
                            run.koto.network.TorStatus.CONNECTING -> Color(0xFFFFD60A)
                            else -> null
                        },
                    )
                }
            }

            item(key = "about") {
                Section(label = "О ПРИЛОЖЕНИИ") {
                    NavRow(
                        icon     = Icons.Default.Info,
                        iconTint = Color(0xFF98989D),
                        title    = "Версия",
                        value    = "1.0.0",
                        onClick  = {},
                    )
                    RowDivider()
                    NavRow(
                        icon     = Icons.Default.Email,
                        iconTint = Color(0xFF5E5CE6),
                        title    = "Написать нам",
                        value    = "",
                        onClick  = {},
                    )
                }
            }

            item(key = "backup") {
                Section(
                    label      = "ПРИВАТНЫЙ КЛЮЧ",
                    labelColor = KotoTheme.colors.warning.copy(alpha = 0.7f),
                    cardBg     = Color(0xFF1F1A0E),
                    cardBorder = KotoTheme.colors.warning.copy(alpha = 0.12f),
                    topGlow    = KotoTheme.colors.warning.copy(alpha = 0.22f),
                ) {
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(KotoTheme.colors.warning.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Lock, null,
                                tint     = KotoTheme.colors.warning,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                        Column(
                            modifier            = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                "Хранится только на устройстве",
                                style = MaterialTheme.typography.bodyMedium,
                                color = KotoTheme.colors.warning,
                            )
                            Text(
                                "При потере телефона аккаунт восстановить невозможно. Экспорт ключей будет добавлен в следующей версии.",
                                style = MaterialTheme.typography.bodySmall,
                                color = KotoTheme.colors.warning.copy(alpha = 0.65f),
                            )
                        }
                    }
                }
            }

            item(key = "danger") {
                Section(
                    label      = "ОПАСНАЯ ЗОНА",
                    labelColor = KotoTheme.colors.error.copy(alpha = 0.7f),
                    cardBg     = Color(0xFF200E0E),
                    cardBorder = KotoTheme.colors.error.copy(alpha = 0.15f),
                    topGlow    = KotoTheme.colors.error.copy(alpha = 0.25f),
                ) {
                    DangerRow(
                        icon    = Icons.Default.DeleteForever,
                        title   = "Удалить аккаунт",
                        onClick = {},
                    )
                }
            }

            // Bottom spacer clears the floating nav bar (~88dp) + breathing room.
            item { Spacer(Modifier.height(120.dp)) }
        }
        } // Box (nestedScroll)
    }

    if (state.showRelayDialog) {
        RelayDialog(
            current   = state.customRelayUrl,
            onSave    = viewModel::saveCustomRelayUrl,
            onDismiss = viewModel::hideCustomRelay,
        )
    }

    if (state.showProxyDialog) {
        ProxyDialog(
            current   = state.proxyUri,
            error     = state.proxyError,
            onSave    = { viewModel.saveProxyUri(it) },
            onDismiss = viewModel::hideProxyDialog,
        )
    }

    if (showEditNameDialog) {
        EditDisplayNameDialog(
            current   = state.displayName,
            onSave    = { newName ->
                viewModel.saveDisplayName(newName)
                showEditNameDialog = false
            },
            onDismiss = { showEditNameDialog = false },
        )
    }
}

// ─── Top bar ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopBar(onBack: () -> Unit) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = KotoTheme.colors.onSurface)
            }
        },
        title = {
            Text("Настройки", style = MaterialTheme.typography.titleLarge, color = KotoTheme.colors.onSurface)
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = KotoTheme.colors.background),
    )
}

// ─── Profile card ─────────────────────────────────────────────────────────────
// Avatar holds the gradient color. Card background is always neutral dark.
// No tinted card backgrounds — they look muddy on dark.

@Composable
private fun ProfileCard(
    accountId         : String,
    displayName       : String,
    avatarDisplayUrl  : String,
    isUploadingAvatar : Boolean,
    hasAvatar         : Boolean,
    overscrollScale   : Float = 1f,
    onCopyId          : () -> Unit,
    onAvatarSelected  : (android.net.Uri) -> Unit,
    onRemoveAvatar    : () -> Unit,
    onEditName        : () -> Unit = {},
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) { delay(2000); copied = false }
    }

    val gradientColors = remember(accountId) { avatarGradient(accountId.ifBlank { "koto" }) }
    val initial = displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "K"

    // ── Bottom sheet ──────────────────────────────────────────────────────────
    var showSheet by remember { mutableStateOf(false) }

    // Camera capture
    var cameraFileUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(TakePicture()) { captured ->
        if (captured) cameraFileUri?.let(onAvatarSelected)
    }
    val cameraPermission = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        if (granted) {
            val tmp = File.createTempFile("avatar_", ".jpg", context.cacheDir)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tmp)
            cameraFileUri = uri
            cameraLauncher.launch(uri)
        }
    }

    // Gallery picker (images only)
    val galleryLauncher = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        uri?.let(onAvatarSelected)
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest  = { showSheet = false },
            containerColor    = KotoTheme.colors.surface,
            dragHandle        = {
                Box(
                    Modifier
                        .padding(vertical = 12.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(KotoTheme.colors.onSurfaceMuted)
                )
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "Фото профиля",
                    style    = MaterialTheme.typography.titleMedium,
                    color    = TextPrimary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                )
                // Camera
                AvatarSheetOption(
                    icon    = Icons.Default.CameraAlt,
                    tint    = Color(0xFF0A84FF),
                    label   = "Сделать фото",
                    onClick = {
                        showSheet = false
                        val hasPerm = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasPerm) {
                            val tmp = File.createTempFile("avatar_", ".jpg", context.cacheDir)
                            val uri = FileProvider.getUriForFile(
                                context, "${context.packageName}.fileprovider", tmp
                            )
                            cameraFileUri = uri
                            cameraLauncher.launch(uri)
                        } else {
                            cameraPermission.launch(Manifest.permission.CAMERA)
                        }
                    }
                )
                // Gallery
                AvatarSheetOption(
                    icon    = Icons.Default.PhotoLibrary,
                    tint    = Color(0xFF30D158),
                    label   = "Выбрать из галереи",
                    onClick = {
                        showSheet = false
                        galleryLauncher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                    }
                )
                // Remove — only shown when avatar is set
                if (hasAvatar) {
                    HorizontalDivider(
                        modifier  = Modifier.padding(vertical = 6.dp),
                        color     = KotoTheme.colors.divider,
                        thickness = 0.5.dp,
                    )
                    AvatarSheetOption(
                        icon    = Icons.Default.DeleteOutline,
                        tint    = KotoTheme.colors.error,
                        label   = "Удалить фото",
                        onClick = { showSheet = false; onRemoveAvatar() }
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .settingsCard(radius = 52f)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ── Avatar with camera badge ──────────────────────────────────────────
        // scale() is applied in the graphics layer — layout stays 76dp but the
        // rendered circle grows when the user pulls the list past the top.
        Box(
            modifier         = Modifier.size(76.dp).scale(overscrollScale),
            contentAlignment = Alignment.BottomEnd,
        ) {
            // Image or gradient circle
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !isUploadingAvatar) { showSheet = true },
                contentAlignment = Alignment.Center,
            ) {
                var imageError by remember(avatarDisplayUrl) { mutableStateOf(false) }
                if (avatarDisplayUrl.isNotBlank() && !imageError) {
                    AsyncImage(
                        model              = avatarDisplayUrl,
                        contentDescription = null,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize(),
                        onError            = { imageError = true },
                    )
                } else {
                    Box(
                        modifier         = Modifier
                            .fillMaxSize()
                            .background(Brush.linearGradient(gradientColors)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = initial, color = Color.White, fontSize = 28.sp)
                    }
                }

                // Dark overlay + spinner while uploading
                if (isUploadingAvatar) {
                    Box(
                        modifier         = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(26.dp),
                            color       = Color.White,
                            strokeWidth = 2.5.dp,
                        )
                    }
                }
            }

            // Camera badge — shown when not uploading
            if (!isUploadingAvatar) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(KotoTheme.colors.primary)
                        .border(2.dp, KotoTheme.colors.background, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.CameraAlt, null,
                        tint     = Color.White,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }

        // Name — tap to edit
        Row(
            modifier              = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onEditName)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text  = displayName.ifBlank { "Пользователь Koto" },
                style = MaterialTheme.typography.titleMedium,
                color = KotoTheme.colors.onSurface,
            )
            Icon(
                Icons.Default.Edit,
                contentDescription = "Изменить имя",
                tint               = KotoTheme.colors.onSurfaceLow,
                modifier           = Modifier.size(15.dp),
            )
        }

        // Public key (account ID) block
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(KotoTheme.colors.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Label with key icon — explicitly "public"
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Key, null,
                    tint     = KotoTheme.colors.onSurfaceLow,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    "Публичный ключ (ваш адрес)",
                    style    = MaterialTheme.typography.labelSmall,
                    color    = KotoTheme.colors.onSurfaceLow,
                    fontSize = 11.sp,
                )
            }
            Text(
                text  = accountId.chunked(8).joinToString("  "),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily    = FontFamily.Monospace,
                    lineHeight    = 20.sp,
                    letterSpacing = 0.3.sp,
                ),
                color = TextPrimary.copy(alpha = 0.8f),
            )
            Text(
                "Можно сообщать другим — не секретный",
                style    = MaterialTheme.typography.labelSmall,
                color    = KotoTheme.colors.success.copy(alpha = 0.7f),
                fontSize = 10.sp,
            )
            // Copy button — animated feedback
            val btnColor by animateColorAsState(
                targetValue  = if (copied) KotoTheme.colors.success else KotoTheme.colors.primary,
                animationSpec = tween(durationMillis = 250),
                label        = "copyColor",
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        animateColorAsState(
                            targetValue  = if (copied) KotoTheme.colors.success.copy(alpha = 0.12f)
                                           else Color.Transparent,
                            animationSpec = tween(250),
                            label        = "copyBg",
                        ).value
                    )
                    .clickable(enabled = !copied) {
                        onCopyId()
                        copied = true
                    }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                AnimatedContent(
                    targetState  = copied,
                    transitionSpec = {
                        (fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.7f))
                            .togetherWith(fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.7f))
                    },
                    label = "copyIcon",
                ) { isCopied ->
                    Icon(
                        imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint     = btnColor,
                        modifier = Modifier.size(13.dp),
                    )
                }
                AnimatedContent(
                    targetState  = copied,
                    transitionSpec = {
                        (fadeIn(tween(200)) + slideInVertically(tween(200)) { -it })
                            .togetherWith(fadeOut(tween(150)) + slideOutVertically(tween(150)) { it })
                    },
                    label = "copyText",
                ) { isCopied ->
                    Text(
                        text  = if (isCopied) "Скопировано" else "Скопировать",
                        style = MaterialTheme.typography.labelMedium,
                        color = btnColor,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

// ─── Avatar sheet option row ──────────────────────────────────────────────────

@Composable
private fun AvatarSheetOption(
    icon    : ImageVector,
    tint    : Color,
    label   : String,
    onClick : () -> Unit,
) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier         = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(tint.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyMedium, color = KotoTheme.colors.onSurface)
    }
}

// ─── Section ──────────────────────────────────────────────────────────────────

@Composable
private fun Section(
    label      : String,
    labelColor : Color = KotoTheme.colors.onSurfaceLow,
    cardBg     : Color = KotoTheme.colors.surface,
    cardBorder : Color = KotoTheme.colors.divider,
    topGlow    : Color = KotoTheme.colors.onSurface.copy(alpha = 0.06f),
    content    : @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            text     = label,
            fontSize = 11.sp,
            color    = labelColor,
            modifier = Modifier.padding(horizontal = 6.dp),
            letterSpacing = 0.5.sp,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(cardBg)
                .border(0.5.dp, cardBorder, RoundedCornerShape(16.dp))
                .drawBehind {
                    drawLine(
                        color       = topGlow,
                        start       = Offset(48f, 0f),
                        end         = Offset(size.width - 48f, 0f),
                        strokeWidth = 1f,
                    )
                },
            content = content,
        )
    }
}

// ─── Row divider (iOS inset — skips icon column) ──────────────────────────────

@Composable
private fun RowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 60.dp)
            .height(0.5.dp)
            .background(KotoTheme.colors.divider)
    )
}

// ─── Icon container ───────────────────────────────────────────────────────────
// Telegram style: 34dp square, 8dp radius, tinted background, no border

@Composable
private fun IconPill(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

// ─── Switch row ───────────────────────────────────────────────────────────────

@Composable
private fun SwitchRow(
    icon       : ImageVector,
    iconTint   : Color,
    title      : String,
    subtitle   : String,
    checked    : Boolean,
    onToggle   : (Boolean) -> Unit,
    badge      : String? = null,
    badgeColor : Color?  = null,
) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconPill(icon, iconTint)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    text  = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = KotoTheme.colors.onSurface,
                )
                if (badge != null) {
                    // Active badge — small pill
                    val pillColor = badgeColor ?: KotoTheme.colors.success
                    Text(
                        text     = badge,
                        fontSize = 10.sp,
                        color    = pillColor,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(pillColor.copy(alpha = 0.14f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            if (subtitle.isNotBlank()) {
                Text(
                    text  = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = KotoTheme.colors.onSurfaceLow,
                )
            }
        }
        KotoSwitch(
            checked         = checked,
            onCheckedChange = onToggle,
        )
    }
}

// ─── Nav row ──────────────────────────────────────────────────────────────────

@Composable
private fun NavRow(
    icon     : ImageVector,
    iconTint : Color,
    title    : String,
    value    : String,
    onClick  : () -> Unit,
) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconPill(icon, iconTint)
        Text(
            text     = title,
            style    = MaterialTheme.typography.bodyMedium,
            color    = TextPrimary,
            modifier = Modifier.weight(1f),
        )
        if (value.isNotBlank()) {
            Text(
                text  = value,
                style = MaterialTheme.typography.bodySmall,
                color = KotoTheme.colors.onSurfaceLow,
            )
        }
        Icon(
            Icons.Default.ChevronRight, null,
            tint     = KotoTheme.colors.onSurfaceMuted,
            modifier = Modifier.size(16.dp),
        )
    }
}

// ─── Danger row ───────────────────────────────────────────────────────────────

@Composable
private fun DangerRow(
    icon    : ImageVector,
    title   : String,
    onClick : () -> Unit,
) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(KotoTheme.colors.error.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = KotoTheme.colors.error, modifier = Modifier.size(18.dp))
        }
        Text(
            text     = title,
            style    = MaterialTheme.typography.bodyMedium,
            color    = KotoTheme.colors.error,
            modifier = Modifier.weight(1f),
        )
    }
}

// ─── Relay URL dialog ─────────────────────────────────────────────────────────

@Composable
private fun RelayDialog(
    current   : String,
    onSave    : (String) -> Unit,
    onDismiss : () -> Unit,
) {
    var text by remember { mutableStateOf(current) }

    AlertDialog(
        onDismissRequest   = onDismiss,
        containerColor     = Color(0xFF1C1C1E),
        shape              = RoundedCornerShape(20.dp),
        title = {
            Text("Адрес relay", color = KotoTheme.colors.onSurface)
        },
        text = {
            OutlinedTextField(
                value         = text,
                onValueChange = { text = it },
                placeholder   = { Text("https://relay.example.com", color = KotoTheme.colors.onSurfaceLow) },
                singleLine    = true,
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = KotoTheme.colors.primary,
                    unfocusedBorderColor = KotoTheme.colors.divider,
                    focusedTextColor     = TextPrimary,
                    unfocusedTextColor   = TextPrimary,
                    cursorColor          = KotoTheme.colors.primary,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) {
                Text("Сохранить", color = KotoTheme.colors.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = KotoTheme.colors.onSurfaceLow)
            }
        },
    )
}

// ─── Security warning banner ─────────────────────────────────────────────────

@Composable
private fun SecurityWarningBanner(status: run.koto.security.SecurityManager.SecurityStatus) {
    val colors = KotoTheme.colors
    val messages = buildList {
        if (status.isRooted)           add("Устройство рутировано")
        if (status.isDebuggerAttached) add("Подключён отладчик")
        if (status.hasRETools)         add("Обнаружены инструменты анализа")
        if (status.isTampered)         add("Подпись приложения изменена")
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.error.copy(alpha = 0.12f))
            .border(0.5.dp, colors.error.copy(alpha = 0.30f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Default.WarningAmber, null,
            tint     = colors.error,
            modifier = Modifier.size(22.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Устройство не доверенное",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.error,
            )
            Text(
                messages.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = colors.error.copy(alpha = 0.75f),
            )
            Text(
                "Сообщения шифруются, но Koto не может гарантировать защиту ключей на скомпрометированном устройстве.",
                style    = MaterialTheme.typography.bodySmall,
                color    = colors.onSurfaceLow,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

// ─── Proxy (Shadowsocks/VMess/SOCKS5) dialog ──────────────────────────────────

@Composable
private fun ProxyDialog(
    current   : String,
    error     : String?,
    onSave    : (String) -> Unit,
    onDismiss : () -> Unit,
) {
    var text by remember { mutableStateOf(current) }
    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = KotoTheme.colors.surface,
        shape            = RoundedCornerShape(20.dp),
        title = {
            Text("Настройка прокси", color = KotoTheme.colors.onSurface)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Вставьте ссылку прокси. Поддерживаются форматы:",
                    style = MaterialTheme.typography.bodySmall,
                    color = KotoTheme.colors.onSurfaceLow,
                )
                Text(
                    "• ss://...  (Shadowsocks)\n• vmess://...  (V2Ray)\n• trojan://...\n• socks5://host:port",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = KotoTheme.colors.onSurfaceLow,
                    fontFamily = FontFamily.Monospace,
                )
                OutlinedTextField(
                    value         = text,
                    onValueChange = { text = it },
                    placeholder   = { Text("ss://...", color = KotoTheme.colors.onSurfaceLow) },
                    singleLine    = false,
                    maxLines      = 3,
                    textStyle     = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = KotoTheme.colors.primary,
                        unfocusedBorderColor = KotoTheme.colors.divider,
                        focusedTextColor     = KotoTheme.colors.onSurface,
                        unfocusedTextColor   = KotoTheme.colors.onSurface,
                        cursorColor          = KotoTheme.colors.primary,
                    ),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = {
                        val pasted = clipboard.getText()?.text ?: ""
                        if (pasted.isNotBlank()) text = pasted
                    }) {
                        Icon(
                            Icons.Default.ContentPaste, null,
                            tint     = KotoTheme.colors.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Вставить", color = KotoTheme.colors.primary, fontSize = 13.sp)
                    }
                    if (text.isNotBlank()) {
                        TextButton(onClick = { text = "" }) {
                            Text("Очистить", color = KotoTheme.colors.onSurfaceLow, fontSize = 13.sp)
                        }
                    }
                }
                if (error != null) {
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = KotoTheme.colors.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) {
                Text("Сохранить", color = KotoTheme.colors.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = KotoTheme.colors.onSurfaceLow)
            }
        },
    )
}

// ─── Edit display name dialog ────────────────────────────────────────────────

@Composable
private fun EditDisplayNameDialog(
    current   : String,
    onSave    : (String) -> Unit,
    onDismiss : () -> Unit,
) {
    var text by remember { mutableStateOf(current) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = KotoTheme.colors.surface,
        shape            = RoundedCornerShape(20.dp),
        title = {
            Text("Как вас зовут?", color = KotoTheme.colors.onSurface)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Это имя увидят другие пользователи Koto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = KotoTheme.colors.onSurfaceLow,
                )
                OutlinedTextField(
                    value         = text,
                    onValueChange = { if (it.length <= 64) text = it },
                    placeholder   = { Text("Имя или никнейм", color = KotoTheme.colors.onSurfaceLow) },
                    singleLine    = true,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = KotoTheme.colors.primary,
                        unfocusedBorderColor = KotoTheme.colors.divider,
                        focusedTextColor     = KotoTheme.colors.onSurface,
                        unfocusedTextColor   = KotoTheme.colors.onSurface,
                        cursorColor          = KotoTheme.colors.primary,
                    ),
                )
                Text(
                    "Можно оставить пустым — тогда вас будут видеть как «Пользователь Koto».",
                    style = MaterialTheme.typography.labelSmall,
                    color = KotoTheme.colors.onSurfaceMuted,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.trim()) }) {
                Text("Сохранить", color = KotoTheme.colors.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = KotoTheme.colors.onSurfaceLow)
            }
        },
    )
}
