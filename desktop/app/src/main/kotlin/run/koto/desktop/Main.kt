package run.koto.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.ktor.client.engine.ProxyConfig
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform
import org.slf4j.LoggerFactory
import run.koto.desktop.crypto.security.NativeIntegrity
import run.koto.desktop.data.SessionCoordinator
import run.koto.desktop.data.local.DatabaseFactory
import run.koto.desktop.data.network.TorManager
import run.koto.desktop.data.network.TransportPolicy
import run.koto.desktop.data.repository.NetworkSettingsRepositoryImpl
import run.koto.desktop.di.appModule
import run.koto.desktop.di.bootTimeProxy
import run.koto.desktop.di.bootTimeTorManager
import run.koto.desktop.ui.KotoApp
import run.koto.desktop.ui.chrome.JbrWindowDecorations
import run.koto.desktop.ui.chrome.KotoTitleBar
import run.koto.desktop.ui.chrome.maximizeTransform
import run.koto.desktop.ui.chrome.rememberWindowMaximizeController
import run.koto.desktop.ui.theme.KotoTheme
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

private val log = LoggerFactory.getLogger("run.koto.desktop.Main")

fun main() {
    // Before Skiko/Swing: AWT resize erase + Skiko buffering / Linux render path (see DesktopRendering).
    DesktopRendering.applyBeforeSkikoOrSwingLoads()

    // 0. Single-instance guard. Must run BEFORE any heavy work (DB open, Tor bootstrap,
    //    Koin start) so a second launch exits cheaply without touching shared state.
    //    We intentionally DO NOT close() the lock before exit — the JVM releases file
    //    locks on process termination, and an explicit close() during shutdown races
    //    with background daemon threads that may still be writing to the DB.
    val instance = SingleInstance(appDataDir().resolve("instance.lock"))
    if (!instance.tryAcquire()) {
        log.info("another Koto instance is already running — exiting")
        exitProcess(0)
    }

    // 1. Verify bundled Rust crypto before JNA touches it.
    runCatching { NativeIntegrity.applyOrFail() }.onFailure {
        log.error("native integrity check failed — aborting startup", it)
        exitProcess(2)
    }

    // 2. Read network preferences (Tor enabled? user proxy?) from the DB.
    //    Bootstrapping Tor needs to happen BEFORE Koin wires HttpClients so the
    //    main client has the right proxy from its first call.
    val (proxy, torMgr) = runBlocking { bootstrapTransport() }

    bootTimeProxy = proxy
    bootTimeTorManager = torMgr

    // 3. Start Koin with our pre-resolved proxy + Tor manager.
    startKoin { modules(appModule) }
    KoinPlatform.getKoin().get<SessionCoordinator>().start()
    // FileKit needs an app id to scope its temp / cache directories. Called
    // before the picker is invoked anywhere — once, here, is enough.
    runCatching { io.github.vinceglb.filekit.FileKit.init(appId = "Koto") }
        .onFailure { log.warn("FileKit.init failed", it) }
    // Wire the in-process byte counter (HttpClient interceptors already feed
    // it; this just starts the periodic flush to the SQLite stats row).
    run.koto.desktop.data.remote.NetworkByteCounter.bindRepository(
        KoinPlatform.getKoin().get(),
    )

    val windowStore = WindowStateStore(appDataDir().resolve("window.properties"))
    val savedWin    = windowStore.load()

    application {
        val windowState = rememberWindowState(
            position = if (savedWin?.x != null && savedWin.y != null)
                androidx.compose.ui.window.WindowPosition(savedWin.x.dp, savedWin.y.dp)
                else androidx.compose.ui.window.WindowPosition.PlatformDefault,
            size     = DpSize(
                width  = (savedWin?.width  ?: 1100).dp,
                height = (savedWin?.height ?: 760 ).dp,
            ),
            placement = if (savedWin?.maximized == true)
                androidx.compose.ui.window.WindowPlacement.Maximized
                else androidx.compose.ui.window.WindowPlacement.Floating,
        )
        val darkTheme   = remember { mutableStateOf(true) }
        // Logical maximize (animated full-screen) is not always WindowPlacement.Maximized — persist that flag.
        val persistMaximized = remember { mutableStateOf(savedWin?.maximized == true) }
        val onClose     = {
            runCatching {
                windowStore.save(PersistedWindowState(
                    x         = windowState.position.let { p ->
                        if (p is androidx.compose.ui.window.WindowPosition.Absolute) p.x.value.toInt() else null
                    },
                    y         = windowState.position.let { p ->
                        if (p is androidx.compose.ui.window.WindowPosition.Absolute) p.y.value.toInt() else null
                    },
                    width     = windowState.size.width.value.toInt(),
                    height    = windowState.size.height.value.toInt(),
                    maximized = persistMaximized.value,
                ))
            }
            runBlocking { torMgr?.stop() }
            exitApplication()
        }

        // On JBR the window stays decorated so DWM animates natively; JBR
        // hides the caption via WM_NCCALCSIZE. On OpenJDK we go undecorated.
        val undecorated = !JbrWindowDecorations.isAvailable

        Window(
            onCloseRequest = onClose,
            state          = windowState,
            title          = "Koto",
            undecorated    = undecorated,
            resizable      = true,
        ) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            LaunchedEffect(Unit) {
                val minPx = with(density) { java.awt.Dimension(900.dp.roundToPx(), 600.dp.roundToPx()) }
                window.minimumSize = minPx
            }

            KotoTheme(darkTheme = darkTheme.value) {
                val surfaceBg = KotoTheme.colors.background
                val scope   = rememberCoroutineScope()
                val maxCtrl = rememberWindowMaximizeController(
                    window         = window,
                    windowState    = windowState,
                    titleBarHeight = 40.dp,
                    density        = density,
                    onAwtResize    = { DesktopRendering.syncAwtWindowBackground(window, surfaceBg) },
                )
                val isMax by maxCtrl.maximizedState
                LaunchedEffect(isMax) {
                    persistMaximized.value = isMax
                }
                val onToggleMaximize: () -> Unit = {
                    scope.launch { maxCtrl.toggle() }
                }
                LaunchedEffect(surfaceBg) {
                    DesktopRendering.syncAwtWindowBackground(window, surfaceBg)
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(KotoTheme.colors.background),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .maximizeTransform(maxCtrl),
                    ) {
                        KotoTitleBar(
                            windowState      = windowState,
                            isMaximized      = isMax,
                            onToggleMaximize = onToggleMaximize,
                            onClose          = onClose,
                        )
                        KotoApp(
                            modifier       = Modifier.weight(1f).fillMaxWidth(),
                            darkTheme      = darkTheme.value,
                            onToggleTheme  = { darkTheme.value = !darkTheme.value },
                        )
                    }
                }
            }
        }
    }

    // Daemon threads (Ktor CIO, Tokio, SQLite) can keep the JVM alive after
    // the last window closes — force-exit so the next launch doesn't race.
    exitProcess(0)
}

private suspend fun bootstrapTransport(): Pair<ProxyConfig?, TorManager?> {
    val db    = DatabaseFactory.create()
    val repo  = NetworkSettingsRepositoryImpl(db)
    val prefsRaw = repo.snapshot()

    // `-Dkoto.tor=true` / KOTO_TOR=true forces Tor on, overriding prefs.
    val torOverride = (System.getProperty("koto.tor") ?: System.getenv("KOTO_TOR"))?.toBoolean() == true
    val prefs = if (torOverride) prefsRaw.copy(torEnabled = true) else prefsRaw

    if (!prefs.torEnabled) {
        val proxy = TransportPolicy.resolve(
            torState     = run.koto.desktop.domain.model.TorState.DISABLED,
            torSocksPort = null,
            prefs        = prefs,
        )
        return proxy to null
    }

    val torDirs = torDirectories()
    val torMgr  = TorManager(workDir = torDirs.first, cacheDir = torDirs.second)

    log.info("bootstrapping Tor...")
    val ok = torMgr.start()
    if (!ok) {
        log.error("Tor bootstrap failed — user explicitly enabled Tor, refusing to fall back to direct")
        exitProcess(3)
    }

    val proxy = TransportPolicy.resolve(
        torState     = torMgr.state.value,
        torSocksPort = torMgr.socksPort.value,
        prefs        = prefs,
    )
    return proxy to torMgr
}

private fun appDataDir(): Path {
    val os = System.getProperty("os.name").lowercase()
    val base: Path = when {
        os.contains("win") ->
            Path.of(System.getenv("APPDATA") ?: System.getProperty("user.home"), "Koto")
        os.contains("mac") ->
            Path.of(System.getProperty("user.home"), "Library", "Application Support", "Koto")
        else ->
            Path.of(
                System.getenv("XDG_DATA_HOME") ?: (System.getProperty("user.home") + "/.local/share"),
                "koto",
            )
    }
    Files.createDirectories(base)
    return base
}

private fun torDirectories(): Pair<Path, Path> {
    val base  = appDataDir().resolve("tor")
    val work  = base.resolve("work")
    val cache = base.resolve("cache")
    Files.createDirectories(work)
    Files.createDirectories(cache)
    return work to cache
}
