package run.koto.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import run.koto.desktop.ui.nav.NavStack
import run.koto.desktop.ui.nav.NavTransition
import run.koto.desktop.ui.nav.Screen
import run.koto.desktop.ui.screens.PlaceholderScreen
import run.koto.desktop.ui.screens.WelcomePane
import run.koto.desktop.ui.screens.auth.AuthHost
import run.koto.desktop.ui.screens.chatlist.ArchiveScreen
import run.koto.desktop.ui.screens.chatlist.ChatListScreen
import run.koto.desktop.ui.screens.chatlist.ChatListState
import run.koto.desktop.ui.screens.call.CallScreen
import run.koto.desktop.ui.screens.conversation.ConversationScreen
import run.koto.desktop.ui.screens.bots.BotForgeScreen
import run.koto.desktop.ui.screens.bots.BotsScreen
import run.koto.desktop.ui.screens.others.ContactScreen
import run.koto.desktop.ui.screens.others.NewChatScreen
import run.koto.desktop.ui.screens.others.SafetyScreen
import run.koto.desktop.ui.screens.others.StoriesScreen
import run.koto.desktop.ui.screens.overlays.AttachSheet
import run.koto.desktop.ui.screens.overlays.EphemeralSheet
import run.koto.desktop.ui.screens.settings.SettingsScreen
import run.koto.desktop.ui.screens.settings.SettingsSubPlaceholder
import run.koto.desktop.ui.screens.settings.SettingsSubScreen
import run.koto.desktop.ui.theme.KotoEasing
import run.koto.desktop.ui.theme.KotoTheme

/**
 * Desktop shell — Telegram-style two-pane layout that fills the entire window.
 *
 *   ┌──────────────┬───────────────────────────────────────┐
 *   │              │                                       │
 *   │   Sidebar    │              Main pane                │
 *   │  ChatList    │  (Welcome / Chat / Settings / …)      │
 *   │   340 dp     │                                       │
 *   │              │                                       │
 *   └──────────────┴───────────────────────────────────────┘
 *
 * Sidebar always renders [ChatListScreen]. Main pane is driven by a [NavStack] whose
 * current [Screen] determines what's visible. Clicking a chat row pushes [Screen.Chat];
 * clicking Settings pushes [Screen.Settings]; back on a pushed sub-screen pops.
 *
 * Fullscreen overlays (auth, call, lock) are out of scope for this desktop-shell — when
 * Auth / Call land they replace the whole window via a separate overlay layer.
 */
@Composable
fun KotoApp(
    modifier      : Modifier      = Modifier,
    darkTheme     : Boolean       = true,
    onToggleTheme : () -> Unit    = {},
) {
    KotoTheme(darkTheme = darkTheme) {
        val nav = remember { NavStack(initial = Screen.Empty) }
        val chatListState = remember { ChatListState() }

        val authVm: run.koto.desktop.ui.screens.auth.AuthViewModel = org.koin.compose.koinInject()
        val authRepo: run.koto.desktop.domain.repository.AuthRepository = org.koin.compose.koinInject()
        val profileRepo: run.koto.desktop.domain.repository.ProfileRepository = org.koin.compose.koinInject()
        val session by authVm.session.collectAsState(initial = null)

        var attachOpen    by remember { mutableStateOf(false) }
        var ephemeralOpen by remember { mutableStateOf(false) }
        var emojiOpen     by remember { mutableStateOf(false) }
        var emojiPicked   by remember { mutableStateOf<String?>(null) }
        var ephemeralSecs by remember { mutableStateOf(0L) }
        var offlineBanner by remember { mutableStateOf(false) }
        var profile       by remember { mutableStateOf<run.koto.desktop.domain.model.Profile?>(null) }
        var profileTick   by remember { mutableStateOf(0) }
        var deviceCount   by remember { mutableStateOf<Int?>(null) }
        var devicesTick   by remember { mutableStateOf(0) }
        val preferencesRepo: run.koto.desktop.domain.repository.PreferencesRepository = org.koin.compose.koinInject()
        val preferences by preferencesRepo.observe().collectAsState(initial = run.koto.desktop.domain.model.AppPreferences())

        // Live "ДАННЫЕ И ХРАНИЛИЩЕ" detail rows. Storage is recomputed on demand
        // (it's the heaviest call, requires walking the cache dir + a SQL group-by),
        // while auto-download and network usage flow from observable repos and
        // re-render any time the underlying tables change.
        val storageRepo:       run.koto.desktop.domain.repository.StorageRepository       = org.koin.compose.koinInject()
        val autoDownloadRepo:  run.koto.desktop.domain.repository.AutoDownloadRepository  = org.koin.compose.koinInject()
        val networkStatsRepo:  run.koto.desktop.domain.repository.NetworkStatsRepository  = org.koin.compose.koinInject()
        var storageInfo by remember { mutableStateOf<run.koto.desktop.domain.model.StorageInfo?>(null) }
        var storageTick by remember { mutableStateOf(0) }
        val autoDownload by autoDownloadRepo.observe().collectAsState(initial = run.koto.desktop.domain.model.AutoDownloadPrefs())
        val networkStats by networkStatsRepo.observe().collectAsState(initial = run.koto.desktop.domain.model.NetworkStats())
        androidx.compose.runtime.LaunchedEffect(session?.accountId, nav.current, storageTick) {
            // Refresh storage row whenever the user lands on Settings — cheap enough
            // for a sub-200-ms group-by and avoids a stale "2.4 ГБ" label.
            if (nav.current is Screen.Settings) {
                storageInfo = runCatching { storageRepo.snapshot() }.getOrNull()
            }
        }
        androidx.compose.runtime.LaunchedEffect(session?.accountId, profileTick) {
            profile = session?.let { profileRepo.me().getOrNull() }
        }
        androidx.compose.runtime.LaunchedEffect(session?.accountId, devicesTick) {
            deviceCount = session?.let { authRepo.listDevices().getOrNull()?.size }
        }

        if (session == null) {
            Box(modifier.fillMaxSize()) {
                AuthHost(viewModel = authVm)
            }
            return@KotoTheme
        }
        val sess = session!!

        // Call overlays render ABOVE the main two-pane layout. When a Screen.Call is
        // active we still keep the chat nav stack intact so ending the call returns
        // to the same conversation underneath.
        val activeCall = nav.current as? Screen.Call
        if (activeCall != null) {
            Box(modifier.fillMaxSize()) {
                CallScreen(
                    peerId = activeCall.peerId,
                    video  = activeCall.video,
                    onEnd  = { nav.pop() },
                )
            }
            return@KotoTheme
        }

        val rootFocus = remember { androidx.compose.ui.focus.FocusRequester() }
        androidx.compose.runtime.LaunchedEffect(Unit) { rootFocus.requestFocus() }

        Box(
            modifier = modifier
                .fillMaxSize()
                .focusRequester(rootFocus)
                .focusable()
                .onPreviewKeyEvent { e ->
                    if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val ctrl = e.isCtrlPressed
                    when {
                        ctrl && e.key == androidx.compose.ui.input.key.Key.K -> {
                            nav.resetTo(Screen.NewChat); true
                        }
                        ctrl && e.key == androidx.compose.ui.input.key.Key.N -> {
                            nav.resetTo(Screen.NewChat); true
                        }
                        ctrl && e.key == androidx.compose.ui.input.key.Key.Comma -> {
                            nav.resetTo(Screen.Settings); true
                        }
                        e.key == androidx.compose.ui.input.key.Key.Escape -> {
                            when {
                                attachOpen     -> { attachOpen = false; true }
                                ephemeralOpen  -> { ephemeralOpen = false; true }
                                emojiOpen      -> { emojiOpen = false; true }
                                nav.depth > 1  -> { nav.pop(); true }
                                else           -> false
                            }
                        }
                        else -> false
                    }
                },
        ) {
            Row(
                modifier = Modifier.fillMaxSize().background(KotoTheme.colors.background),
            ) {
                // ── Sidebar ─────────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .width(SIDEBAR_WIDTH_DP.dp)
                        .fillMaxHeight()
                        .background(KotoTheme.colors.background),
                ) {
                    ChatListScreen(
                        state          = chatListState,
                        selectedChatId = (nav.current as? Screen.Chat)?.convId,
                        onOpenChat     = { id ->
                            if ((nav.current as? Screen.Chat)?.convId != id) {
                                nav.resetTo(Screen.Chat(id))
                            }
                        },
                        onOpenSettings = { nav.resetTo(Screen.Settings) },
                        onOpenNewChat  = { nav.resetTo(Screen.NewChat) },
                        onOpenStories  = { nav.resetTo(Screen.Stories) },
                        onOpenCamera   = { attachOpen = true },
                        onOpenBots     = { nav.resetTo(Screen.Bots) },
                        onOpenArchive  = { nav.resetTo(Screen.Archive) },
                    )
                }

                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(KotoTheme.colors.separator))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(KotoTheme.colors.background),
                ) {
                    AnimatedContent(
                        modifier       = Modifier.fillMaxSize(),
                        targetState    = nav.current,
                        transitionSpec = {
                            // Snap animated size changes: default size cross-fade fights WM resize
                            // and fullscreen transitions, causing visible jitter on Linux/OpenJDK.
                            transitionFor(nav.lastTransition).using(
                                SizeTransform(clip = false) { _, _ ->
                                    tween(durationMillis = 0, easing = LinearEasing)
                                },
                            )
                        },
                        label          = "main-pane-switcher",
                    ) { screen ->
                        MainPane(
                            screen              = screen,
                            nav                 = nav,
                            chatListState       = chatListState,
                            ephemeralSecs       = ephemeralSecs,
                            emojiPicked         = emojiPicked,
                            onEmojiConsumed     = { emojiPicked = null },
                            onOpenAttach        = { attachOpen = true },
                            onOpenEphemeral     = { ephemeralOpen = true },
                            onOpenEmoji         = { emojiOpen = true },
                            accountId           = sess.accountId,
                            displayName         = profile?.displayName?.takeIf { it.isNotBlank() } ?: "Koto",
                            username            = profile?.username,
                            deviceCount         = deviceCount,
                            privacyPreset       = preferences.privacyPreset,
                            storageDetail       = storageInfo?.let { formatBytesShort(it.totalBytes) },
                            networkDetail       = formatBytesShort(networkStats.sentBytes + networkStats.receivedBytes),
                            autoDownloadDetail  = formatAutoDownload(autoDownload),
                            onProfileChanged    = { profileTick++ },
                            onDevicesChanged    = { devicesTick++ },
                            onToggleTheme       = onToggleTheme,
                            onSignOut           = {
                                authVm.signOut()
                                nav.resetTo(Screen.Empty)
                            },
                        )
                    }
                }
            }

            run.koto.desktop.ui.components.atoms.StatusBanner(
                visible  = offlineBanner,
                text     = "Нет подключения. Сообщения будут отправлены, когда сеть восстановится.",
                kind     = run.koto.desktop.ui.components.atoms.BannerKind.Offline,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            if (attachOpen) {
                AttachSheet(onDismiss = { attachOpen = false })
            }
            if (emojiOpen) {
                run.koto.desktop.ui.screens.overlays.EmojiPicker(
                    onPick    = { e -> emojiPicked = e; emojiOpen = false },
                    onDismiss = { emojiOpen = false },
                )
            }
            if (ephemeralOpen) {
                EphemeralSheet(
                    currentSeconds = ephemeralSecs,
                    onDismiss      = { ephemeralOpen = false },
                    onPick         = { secs -> ephemeralSecs = secs },
                )
            }
        }
    }
}

private fun transitionFor(kind: NavTransition): ContentTransform = when (kind) {
    NavTransition.PUSH ->
        (slideInHorizontally(tween(280, easing = KotoEasing)) { it / 6 } + fadeIn(tween(220)))
            .togetherWith(fadeOut(tween(140)))
    NavTransition.POP ->
        (slideInHorizontally(tween(280, easing = KotoEasing)) { -it / 6 } + fadeIn(tween(220)))
            .togetherWith(fadeOut(tween(140)))
    NavTransition.NONE ->
        fadeIn(tween(180)).togetherWith(fadeOut(tween(120)))
}

@Composable
private fun MainPane(
    screen            : Screen,
    nav               : NavStack,
    chatListState     : ChatListState,
    ephemeralSecs     : Long,
    emojiPicked       : String?,
    onEmojiConsumed   : () -> Unit,
    accountId         : String,
    displayName       : String,
    username          : String?,
    deviceCount       : Int?,
    privacyPreset     : run.koto.desktop.domain.model.PrivacyPreset,
    storageDetail     : String?,
    networkDetail     : String?,
    autoDownloadDetail: String?,
    onProfileChanged  : () -> Unit,
    onDevicesChanged  : () -> Unit,
    onToggleTheme     : () -> Unit,
    onSignOut         : () -> Unit,
    onOpenAttach      : () -> Unit,
    onOpenEphemeral   : () -> Unit,
    onOpenEmoji       : () -> Unit,
) {
    when (screen) {
        is Screen.Empty -> WelcomePane()

        is Screen.Chat -> ConversationScreen(
            chatId               = screen.convId,
            ephemeralOn          = ephemeralSecs > 0L,
            emojiPicked          = emojiPicked,
            onEmojiConsumed      = onEmojiConsumed,
            onOpenContact        = { nav.push(Screen.Contact(screen.convId)) },
            onOpenCall           = { nav.push(Screen.Call(screen.convId, video = false)) },
            onOpenVideo          = { nav.push(Screen.Call(screen.convId, video = true)) },
            onOpenAttach         = onOpenAttach,
            onOpenEmoji          = onOpenEmoji,
            onOpenEphemeralSheet = onOpenEphemeral,
        )

        is Screen.Archive -> ArchiveScreen(
            state      = chatListState,
            onBack     = { nav.resetTo(Screen.Empty) },
            onOpenChat = { id -> nav.resetTo(Screen.Chat(id)) },
        )

        is Screen.Settings -> SettingsScreen(
            profileName        = displayName,
            profileKotoId      = accountId,
            profileUsername    = username,
            deviceCount        = deviceCount,
            privacyPreset      = privacyPreset,
            storageDetail      = storageDetail,
            networkDetail      = networkDetail,
            autoDownloadDetail = autoDownloadDetail,
            onBack             = { nav.resetTo(Screen.Empty) },
            onToggleTheme      = onToggleTheme,
            onOpenSection      = { key -> nav.push(Screen.SettingsSub(key)) },
            onSignOut          = onSignOut,
        )
        is Screen.SettingsSub -> if (screen.section == "profile") {
            // Profile renders an edge-to-edge banner, so it provides its own
            // top bar instead of nesting in SettingsSubScreen's padded shell.
            val vm: run.koto.desktop.ui.screens.settings.ProfileEditViewModel = org.koin.compose.koinInject()
            run.koto.desktop.ui.screens.settings.ProfileEditScreen(
                viewModel = vm,
                onBack    = { onProfileChanged(); nav.pop() },
            )
        } else SettingsSubScreen(
            title   = settingsSubTitle(screen.section),
            onBack  = {
                when (screen.section) {
                    "username" -> onProfileChanged()
                    "devices"  -> onDevicesChanged()
                }
                nav.pop()
            },
        ) {
            when (screen.section) {
                "username" -> {
                    val vm: run.koto.desktop.ui.screens.settings.UsernameViewModel = org.koin.compose.koinInject()
                    run.koto.desktop.ui.screens.settings.UsernameScreen(viewModel = vm)
                }
                "devices" -> {
                    val vm: run.koto.desktop.ui.screens.settings.DevicesViewModel = org.koin.compose.koinInject()
                    run.koto.desktop.ui.screens.settings.DevicesScreen(viewModel = vm)
                }
                "seed" -> {
                    val vm: run.koto.desktop.ui.screens.settings.RecoveryPhraseViewModel = org.koin.compose.koinInject()
                    run.koto.desktop.ui.screens.settings.RecoveryPhraseScreen(viewModel = vm)
                }
                "privacy" -> {
                    val vm: run.koto.desktop.ui.screens.settings.PrivacyViewModel = org.koin.compose.koinInject()
                    run.koto.desktop.ui.screens.settings.PrivacyScreen(viewModel = vm)
                }
                "safety" -> {
                    val vm: run.koto.desktop.ui.screens.safety.SafetyListViewModel = org.koin.compose.koinInject()
                    run.koto.desktop.ui.screens.safety.SafetyListScreen(
                        viewModel = vm,
                        onOpen    = { id -> nav.push(Screen.SafetyDetail(id)) },
                    )
                }
                "storage" -> {
                    val vm: run.koto.desktop.ui.screens.storage.StorageViewModel = org.koin.compose.koinInject()
                    run.koto.desktop.ui.screens.storage.StorageScreen(viewModel = vm)
                }
                "auto" -> {
                    val vm: run.koto.desktop.ui.screens.storage.AutoDownloadViewModel = org.koin.compose.koinInject()
                    run.koto.desktop.ui.screens.storage.AutoDownloadScreen(viewModel = vm)
                }
                "network" -> {
                    val vm: run.koto.desktop.ui.screens.storage.NetworkStatsViewModel = org.koin.compose.koinInject()
                    run.koto.desktop.ui.screens.storage.NetworkStatsScreen(viewModel = vm)
                }
                else -> SettingsSubPlaceholder(sectionKey = screen.section)
            }
        }

        is Screen.NewChat  -> NewChatScreen(
            onBack       = { nav.pop() },
            onOpenChat   = { id -> nav.resetTo(Screen.Chat(id)) },
            onCreateGroup = { nav.push(Screen.NewGroup) },
            ownAccountId = accountId,
        )
        is Screen.NewGroup -> run.koto.desktop.ui.screens.others.NewGroupScreen(
            onBack       = { nav.pop() },
            onOpenChat   = { id -> nav.resetTo(Screen.Chat(id)) },
        )
        is Screen.Contact  -> ContactScreen(
            contactId   = screen.id,
            onBack      = { nav.pop() },
            onOpenChat  = { id -> nav.resetTo(Screen.Chat(id)) },
            onOpenCall  = { nav.push(Screen.Call(screen.id, video = false)) },
            onOpenVideo = { nav.push(Screen.Call(screen.id, video = true)) },
        )
        is Screen.Stories  -> StoriesScreen(onBack = { nav.pop() })
        is Screen.Safety   -> SafetyScreen(peerName = "собеседник", onBack = { nav.pop() })
        is Screen.SafetyDetail -> {
            val vm: run.koto.desktop.ui.screens.safety.SafetyDetailViewModel = org.koin.compose.koinInject()
            run.koto.desktop.ui.screens.safety.SafetyDetailScreen(
                convId    = screen.convId,
                viewModel = vm,
                onBack    = { nav.pop() },
            )
        }
        is Screen.Bots     -> BotsScreen(
            onBack         = { nav.resetTo(Screen.Empty) },
            onOpenBot      = { id -> nav.resetTo(Screen.Chat(id)) },
            onOpenBotForge = { nav.push(Screen.BotForge()) },
        )
        is Screen.BotForge -> BotForgeScreen(
            onBack = { nav.pop() },
            onDone = { nav.pop() },
        )

        is Screen.Welcome,
        is Screen.Register,
        is Screen.Login,
        is Screen.Lock     -> PlaceholderScreen("Overlay misrouted", "These should render as fullscreen overlays.", onNext = { nav.resetTo(Screen.Empty) })

        is Screen.Call     -> PlaceholderScreen("Call", "Call rendered as fullscreen overlay above app.", onNext = { nav.pop() })
    }
}

private fun settingsSubTitle(key: String): String = when (key) {
    "profile"       -> "Профиль"
    "kotoid"        -> "Мой Koto ID"
    "seed"          -> "Фраза восстановления"
    "devices"       -> "Связанные устройства"
    "username"      -> "Имя пользователя"
    "privacy"       -> "Приватность"
    "ephemeral"     -> "Исчезающие сообщения"
    "screenlock"    -> "Блокировка экрана"
    "safety"        -> "Проверка безопасности"
    "sealed"        -> "Закрытые отправители"
    "storage"       -> "Хранилище"
    "network"       -> "Использование сети"
    "auto"          -> "Автозагрузка"
    "theme"         -> "Тема и цвет"
    "font"          -> "Размер шрифта"
    "wallpaper"     -> "Обои чатов"
    "notifications" -> "Уведомления"
    "calls"         -> "Звонки"
    "help"          -> "Справка"
    "about"         -> "О Koto"
    else            -> key
}

private const val SIDEBAR_WIDTH_DP = 340

/** "0 Б" / "12.8 МБ" — same heuristic as the Storage/Network screens, kept short
 *  for the Settings detail row. */
private fun formatBytesShort(bytes: Long): String {
    if (bytes <= 0L) return "0 Б"
    val units = arrayOf("Б", "КБ", "МБ", "ГБ", "ТБ")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024.0 && i < units.lastIndex) { v /= 1024.0; i++ }
    return if (i == 0) "${bytes} ${units[i]}" else String.format("%.1f %s", v, units[i])
}

/** "Фото · Видео · Файлы · Голос" — only the enabled types, joined by `·`.
 *  Empty list collapses to "выкл" so the row never renders a bare separator. */
private fun formatAutoDownload(prefs: run.koto.desktop.domain.model.AutoDownloadPrefs): String {
    val on = buildList {
        if (prefs.photos) add("Фото")
        if (prefs.videos) add("Видео")
        if (prefs.files)  add("Файлы")
        if (prefs.voice)  add("Голос")
    }
    return if (on.isEmpty()) "выкл" else on.joinToString(" · ")
}
