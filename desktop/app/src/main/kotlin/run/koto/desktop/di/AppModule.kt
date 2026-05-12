package run.koto.desktop.di

import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyConfig
import io.ktor.client.plugins.auth.providers.BearerTokens
import kotlinx.coroutines.flow.SharedFlow
import org.koin.core.qualifier.named
import org.koin.dsl.module
import run.koto.desktop.crypto.CryptoStore
import run.koto.desktop.crypto.KotoCryptoProvider
import run.koto.desktop.crypto.UniffiKotoCryptoProvider
import run.koto.desktop.crypto.security.FileBackedVault
import run.koto.desktop.crypto.security.KeyringVault
import run.koto.desktop.crypto.security.LocalAead
import run.koto.desktop.crypto.security.MasterKeyManager
import run.koto.desktop.crypto.security.SecretVault
import run.koto.desktop.data.PrekeyTopUp
import run.koto.desktop.data.SessionCoordinator
import run.koto.desktop.data.local.DatabaseFactory
import run.koto.desktop.data.local.SeedStore
import run.koto.desktop.data.local.SessionStore
import run.koto.desktop.data.local.SqlCryptoStore
import run.koto.desktop.data.local.db.KotoDb
import run.koto.desktop.data.network.TorManager
import run.koto.desktop.data.remote.HttpClientFactory
import run.koto.desktop.data.remote.api.AuthApi
import run.koto.desktop.data.remote.api.SessionsApi
import run.koto.desktop.data.remote.api.ChatApi
import run.koto.desktop.data.remote.api.MediaApi
import run.koto.desktop.data.remote.api.NotificationApi
import run.koto.desktop.data.remote.api.UserApi
import run.koto.desktop.data.repository.AuthRepositoryImpl
import run.koto.desktop.data.repository.ChatRepositoryImpl
import run.koto.desktop.data.repository.ContactsRepositoryImpl
import run.koto.desktop.data.repository.ConversationRepositoryImpl
import run.koto.desktop.data.repository.MediaRepositoryImpl
import run.koto.desktop.data.repository.NetworkSettingsRepositoryImpl
import run.koto.desktop.data.repository.ProfileRepositoryImpl
import run.koto.desktop.data.ws.KotoWebSocket
import run.koto.desktop.data.ws.WebSocketEventDispatcher
import run.koto.desktop.domain.model.WebSocketEvent
import run.koto.desktop.domain.repository.AuthRepository
import run.koto.desktop.domain.repository.ChatRepository
import run.koto.desktop.domain.repository.ContactsRepository
import run.koto.desktop.domain.repository.ConversationRepository
import run.koto.desktop.domain.repository.MediaRepository
import run.koto.desktop.domain.repository.NetworkSettingsRepository
import run.koto.desktop.domain.repository.ProfileRepository
import java.nio.file.Files
import java.nio.file.Path

/**
 * Application configuration supplied via system properties or environment.
 *
 * TLS pinning: comma-separated hex SHA-256 SPKI fingerprints in `koto.tls.pins`
 * (or env `KOTO_TLS_PINS`). Pins are enforced only when TLS is enabled and the
 * target host is not in the bypass list.
 */
data class AppConfig(
    val baseHost : String,
    val baseTls  : Boolean,
    val tlsPins  : Set<String>,
    /** REST gateway port on [baseHost] (host side when using docker port maps). */
    val restPort : Int,
    /** WebSocket gateway port on [baseHost]. */
    val wsPort   : Int,
) {
    companion object {
        fun fromEnvironment(): AppConfig {
            val host = System.getProperty("koto.host") ?: System.getenv("KOTO_HOST") ?: "127.0.0.1"
            val tls  = (System.getProperty("koto.tls") ?: System.getenv("KOTO_TLS") ?: "false").toBoolean()
            val pins = (System.getProperty("koto.tls.pins") ?: System.getenv("KOTO_TLS_PINS") ?: "")
                .split(',', ';')
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .toSet()
            val restOverride = System.getProperty("koto.restPort") ?: System.getenv("KOTO_REST_PORT")
            val wsOverride   = System.getProperty("koto.wsPort") ?: System.getenv("KOTO_WS_PORT")
            val restPort = restOverride?.toIntOrNull() ?: if (tls) 443 else 8080
            val wsPort   = wsOverride?.toIntOrNull() ?: if (tls) 9443 else 9080
            return AppConfig(host, tls, pins, restPort, wsPort)
        }
    }
}

private val BARE    = named("bare")
private val MAIN    = named("main")
private val STORAGE = named("storage")

/**
 * Boot-time state resolved in [run.koto.desktop.Main] before Koin starts — the Tor daemon
 * must be fully bootstrapped BEFORE we build HttpClients, otherwise the very first request
 * would go direct. Using globals is ugly but correct: by the time Koin factories fire,
 * these are immutable.
 */
@Volatile var bootTimeProxy: ProxyConfig? = null
@Volatile var bootTimeTorManager: TorManager? = null

val appModule = module {

    single { AppConfig.fromEnvironment() }
    single {
        val c = get<AppConfig>()
        HttpClientFactory.Endpoints(c.baseHost, c.baseTls, c.restPort, c.wsPort)
    }
    single {
        val cfg = get<AppConfig>()
        HttpClientFactory.TlsPolicy(
            pinnedSpkiSha256 = if (cfg.baseTls) cfg.tlsPins else emptySet(),
        )
    }

    // ── Secret vault + master key + at-rest encryption ────────────────────────
    single<SecretVault> {
        val keyring = KeyringVault()
        if (keyring.isAvailable()) keyring
        else FileBackedVault(vaultFallbackPath())
    }
    single { MasterKeyManager(get()) }
    single { LocalAead(get<MasterKeyManager>().getOrCreate()) }

    single<KotoDb> { DatabaseFactory.create() }
    single { SessionStore(get(), get()) }
    single { SeedStore   (get(), get()) }
    single<run.koto.desktop.domain.repository.SeedRepository> {
        run.koto.desktop.data.repository.SeedRepositoryImpl(get())
    }
    single<CryptoStore> { SqlCryptoStore(get(), get()) }

    // ── Network / Tor / user proxy ────────────────────────────────────────────
    single<NetworkSettingsRepository> { NetworkSettingsRepositoryImpl(get()) }
    single<run.koto.desktop.domain.repository.FolderRepository> {
        run.koto.desktop.data.repository.FolderRepositoryImpl(get())
    }
    // The TorManager was booted in Main before Koin; re-expose it here so repositories
    // can read state / offer stop() from the UI when we build it.
    bootTimeTorManager?.let { single { it } }

    // ── HTTP clients ──────────────────────────────────────────────────────────
    single<HttpClient>(BARE) { HttpClientFactory.bare(get(), get(), bootTimeProxy) }
    single { AuthApi(get<HttpClient>(BARE)) }

    single<KotoCryptoProvider> { UniffiKotoCryptoProvider(get()) }

    single<AuthRepository> { AuthRepositoryImpl(get(), { get<UserApi>() }, { get<SessionsApi>() }, get(), get(), get()) }

    single<HttpClient>(MAIN) {
        val store = get<SessionStore>()
        val auth  = get<AuthRepository>()
        HttpClientFactory.main(
            endpoints     = get(),
            tls           = get(),
            proxy         = bootTimeProxy,
            tokenProvider = {
                val s = store.read() ?: return@main null
                BearerTokens(s.accessToken, s.refreshToken)
            },
            refreshTokens = {
                auth.refresh().getOrNull()?.let { BearerTokens(it.accessToken, it.refreshToken) }
            },
        )
    }

    single { ChatApi(get<HttpClient>(MAIN)) }
    single { UserApi(get<HttpClient>(MAIN)) }
    single { SessionsApi(get<HttpClient>(MAIN)) }
    single { NotificationApi(get<HttpClient>(MAIN)) }
    single<HttpClient>(STORAGE) { HttpClientFactory.storage(bootTimeProxy) }
    single { MediaApi(get<HttpClient>(MAIN), get<HttpClient>(STORAGE)) }

    single<KotoWebSocket> {
        val cfg   = get<AppConfig>()
        val store = get<SessionStore>()
        val auth  = get<AuthRepository>()
        KotoWebSocket(
            http          = get<HttpClient>(MAIN),
            wsHost        = cfg.baseHost,
            wsPort        = cfg.wsPort,
            useTls        = cfg.baseTls,
            tokenProvider = { store.read()?.accessToken },
            onAuthFail    = { auth.refresh().isSuccess },
        )
    }
    single<SharedFlow<WebSocketEvent>> { get<KotoWebSocket>().events }

    single<ConversationRepository> { ConversationRepositoryImpl(get(), get(), get()) }
    single<ChatRepository>         { ChatRepositoryImpl(get(), get(), get(), get(), get(), get(), get()) }
    single<run.koto.desktop.domain.repository.PreferencesRepository> {
        run.koto.desktop.data.repository.PreferencesRepositoryImpl(get())
    }
    single<ContactsRepository>     { ContactsRepositoryImpl(get()) }
    single<ProfileRepository>      { ProfileRepositoryImpl(get()) }
    single<MediaRepository>        { MediaRepositoryImpl(get()) }
    single<run.koto.desktop.domain.repository.StorageRepository> {
        run.koto.desktop.data.repository.StorageRepositoryImpl(
            db         = get(),
            appDataDir = run.koto.desktop.data.local.DatabaseFactory.appDataDir(),
        )
    }
    single<run.koto.desktop.domain.repository.AutoDownloadRepository> {
        run.koto.desktop.data.repository.AutoDownloadRepositoryImpl(get())
    }
    single<run.koto.desktop.domain.repository.NetworkStatsRepository> {
        run.koto.desktop.data.repository.NetworkStatsRepositoryImpl(get())
    }

    single { PrekeyTopUp(get(), get()) }
    single { WebSocketEventDispatcher(get(), get(), get(), get()) }
    single { SessionCoordinator(get(), get(), get(), get(), get(), get()) }

    // ── ViewModels ─────────────────────────────────────────────────────────────
    factory { run.koto.desktop.ui.screens.auth.AuthViewModel(get()) }
    factory { run.koto.desktop.ui.screens.settings.UsernameViewModel(get()) }
    factory { run.koto.desktop.ui.screens.settings.DevicesViewModel(get()) }
    factory { run.koto.desktop.ui.screens.settings.RecoveryPhraseViewModel(get()) }
    factory { run.koto.desktop.ui.screens.settings.PrivacyViewModel(get()) }
    factory { run.koto.desktop.ui.screens.settings.ProfileEditViewModel(get(), get()) }
    factory { run.koto.desktop.ui.screens.safety.SafetyListViewModel(get()) }
    factory { run.koto.desktop.ui.screens.safety.SafetyDetailViewModel(get(), get()) }
    factory { run.koto.desktop.ui.screens.storage.StorageViewModel(get()) }
    factory { run.koto.desktop.ui.screens.storage.AutoDownloadViewModel(get()) }
    factory { run.koto.desktop.ui.screens.storage.NetworkStatsViewModel(get()) }
    single  { run.koto.desktop.ui.screens.chatlist.ChatListViewModel(get(), get()) }
    single  { run.koto.desktop.ui.screens.conversation.ConversationViewModel(get(), get()) }
    factory { run.koto.desktop.ui.screens.others.NewChatViewModel(get(), get()) }
    factory { run.koto.desktop.ui.screens.others.NewGroupViewModel(get(), get()) }
}

/**
 * Fallback path for [FileBackedVault] when no OS keystore is available. Uses the same
 * per-user app-data directory as the SQLite file, but with a dedicated filename.
 */
private fun vaultFallbackPath(): Path {
    val os = System.getProperty("os.name").lowercase()
    val base = when {
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
    return base.resolve("vault.properties")
}
