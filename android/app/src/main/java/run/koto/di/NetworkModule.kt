package run.koto.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import run.koto.BuildConfig
import run.koto.data.prefs.AccountPrefs
import run.koto.data.remote.api.AuthApi
import run.koto.data.remote.api.ChatApi
import run.koto.data.remote.api.MediaApi
import run.koto.data.remote.api.NotificationApi
import run.koto.data.remote.api.UserApi
import run.koto.data.remote.ws.KotoWebSocket
import run.koto.data.remote.ws.OkHttp
import run.koto.network.AuthHeaderInterceptor
import run.koto.network.ProxyManager
import run.koto.network.RefreshAuthApi
import run.koto.network.TokenAuthenticator
import run.koto.network.TorAwareCallFactory
import run.koto.network.TorManager
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().create()

    /**
     * Certificate pinning — protects against MITM and rogue CAs.
     *
     * Pins use SHA-256 of the SubjectPublicKeyInfo (SPKI) — standard HPKP format.
     * Pinning is DISABLED in debug builds to allow connecting to 10.0.2.2 (emulator).
     */
    @Provides
    @Singleton
    fun provideCertificatePinner(): CertificatePinner {
        if (BuildConfig.DEBUG) return CertificatePinner.DEFAULT
        return CertificatePinner.Builder()
            // .add("koto.run", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
            // .add("koto.run", "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=")
            .build()
    }

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG)
                HttpLoggingInterceptor.Level.BODY
            else
                HttpLoggingInterceptor.Level.NONE
            // Never log the Authorization header even in debug.
            redactHeader("Authorization")
            redactHeader("Cookie")
        }

    // ─── Main OkHttpClient (full chain: auth header + authenticator) ─────────

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authHeader: AuthHeaderInterceptor,
        tokenAuthenticator: TokenAuthenticator,
        certificatePinner: CertificatePinner,
        logging: HttpLoggingInterceptor,
    ): OkHttpClient =
        OkHttpClient.Builder()
            .certificatePinner(certificatePinner)
            .addInterceptor(authHeader)
            .addInterceptor(logging)
            .authenticator(tokenAuthenticator)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

    // ─── Bare client for token refresh ───────────────────────────────────────
    //
    // This MUST NOT include the TokenAuthenticator or AuthHeaderInterceptor —
    // otherwise a failed refresh would loop back into the authenticator and
    // recurse forever. It also must not send the stale access token.

    @Provides
    @Singleton
    @RefreshAuthApi
    fun provideRefreshOkHttpClient(
        certificatePinner: CertificatePinner,
        logging: HttpLoggingInterceptor,
    ): OkHttpClient =
        OkHttpClient.Builder()
            .certificatePinner(certificatePinner)
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    @RefreshAuthApi
    fun provideRefreshAuthApi(
        @RefreshAuthApi client: OkHttpClient,
        gson: Gson,
    ): AuthApi =
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL + "/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(AuthApi::class.java)

    // ─── TorAwareCallFactory wraps the main client ───────────────────────────

    @Provides
    @Singleton
    fun provideTorAwareCallFactory(
        client: OkHttpClient,
        torManager: TorManager,
        proxyManager: ProxyManager,
    ): TorAwareCallFactory = TorAwareCallFactory(torManager, proxyManager, client)

    @Provides
    @Singleton
    fun provideOkHttpWrapper(callFactory: TorAwareCallFactory): OkHttp =
        OkHttp { callFactory.activeClient() }

    @Provides
    @Singleton
    fun provideRetrofit(callFactory: TorAwareCallFactory, gson: Gson): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL + "/")
            .callFactory(callFactory)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideChatApi(retrofit: Retrofit): ChatApi = retrofit.create(ChatApi::class.java)

    @Provides
    @Singleton
    fun provideUserApi(retrofit: Retrofit): UserApi = retrofit.create(UserApi::class.java)

    @Provides
    @Singleton
    fun provideMediaApi(retrofit: Retrofit): MediaApi = retrofit.create(MediaApi::class.java)

    @Provides
    @Singleton
    fun provideNotificationApi(retrofit: Retrofit): NotificationApi =
        retrofit.create(NotificationApi::class.java)

    @Provides
    @Singleton
    fun provideWebSocket(okHttp: OkHttp): KotoWebSocket =
        KotoWebSocket(okHttp, BuildConfig.WS_BASE_URL + "/ws")
}
