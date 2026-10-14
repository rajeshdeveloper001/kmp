package com.vplayed.shared

import com.vplayed.shared.core.config.SharedConfig
import com.vplayed.shared.core.crypto.CryptoPrimitives
import com.vplayed.shared.core.crypto.TransportCipher
import com.vplayed.shared.core.crypto.platformCryptoPrimitives
import com.vplayed.shared.core.network.ApiCaller
import com.vplayed.shared.core.network.HttpClientFactory
import com.vplayed.shared.auth.AuthFlowConfig
import com.vplayed.shared.auth.AuthRepository
import com.vplayed.shared.auth.DefaultAuthRepository
import com.vplayed.shared.auth.DeviceLimit
import com.vplayed.shared.auth.EmailLoginController
import com.vplayed.shared.auth.LogoutResult
import com.vplayed.shared.auth.OtpController
import com.vplayed.shared.core.network.RequestCredentials
import com.vplayed.shared.core.network.createPlatformHttpEngine
import com.vplayed.shared.core.platform.DeviceInfo
import com.vplayed.shared.core.platform.SharedLogger
import com.vplayed.shared.core.platform.platformLogger
import com.vplayed.shared.core.serialization.SharedJson
import com.vplayed.shared.geo.DefaultGeoRepository
import com.vplayed.shared.geo.GeoRepository
import com.vplayed.shared.geo.GeoStore
import com.vplayed.shared.home.DefaultHomeRepository
import com.vplayed.shared.home.HomeFeedController
import com.vplayed.shared.home.HomeImagePolicy
import com.vplayed.shared.home.HomeMapper
import com.vplayed.shared.home.HomeRepository
import com.vplayed.shared.movies.DefaultMoviesRepository
import com.vplayed.shared.movies.MoviesRepository
import com.vplayed.shared.session.SessionCredentials
import com.vplayed.shared.session.SessionManager
import com.vplayed.shared.settings.CommonSettingsRepository
import com.vplayed.shared.settings.DefaultCommonSettingsRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json

/** What each app supplies to the shared layer. */
class PlatformDependencies(
    val deviceInfo: DeviceInfo,
    val logger: SharedLogger,
    /** Called once per HTTP client; tests pass a `MockEngine`. */
    val engineFactory: () -> HttpClientEngine,
    val cryptoPrimitives: CryptoPrimitives,
) {
    /** Platform defaults: system log, OkHttp / Darwin engine, javax.crypto / CommonCrypto. */
    constructor(deviceInfo: DeviceInfo) : this(
        deviceInfo = deviceInfo,
        logger = platformLogger(),
        engineFactory = ::createPlatformHttpEngine,
        cryptoPrimitives = platformCryptoPrimitives(),
    )
}

/**
 * Manual dependency graph of the shared layer. Each app creates exactly one per process —
 * AndroidJP from a Hilt `@Provides @Singleton`, VPlayed in `MyApp.init` — and reaches every shared
 * service through it. Feature repositories and controllers are added here as they migrate.
 */
class SharedGraph(
    val config: SharedConfig,
    private val platform: PlatformDependencies,
    /**
     * The signed-in or guest session (Phase 5), created by the app over its secure storage and passed
     * in like [geoStore], so app code and tests can use it without the graph.
     */
    val sessionManager: SessionManager,
    /** The geo token and location. VPlayed uses the constructor without it. */
    val geoStore: GeoStore,
) {
    constructor(config: SharedConfig, platform: PlatformDependencies, sessionManager: SessionManager) :
        this(config, platform, sessionManager, GeoStore())

    val json: Json = SharedJson

    val logger: SharedLogger get() = platform.logger

    /** What request headers send: [sessionManager]'s tokens and [geoStore]'s geo token. */
    val requestCredentials: RequestCredentials by lazy { SessionCredentials(sessionManager, geoStore) }

    val transportCipher: TransportCipher by lazy { TransportCipher(platform.cryptoPrimitives) }

    /** VPlayed backend client (headers, base URL, timeouts). */
    val backendHttpClient: HttpClient by lazy {
        HttpClientFactory.backend(platform.engineFactory(), config, platform.deviceInfo, requestCredentials, platform.logger)
    }

    /** Third-party client without VPlayed headers (public IP lookup). */
    val thirdPartyHttpClient: HttpClient by lazy {
        HttpClientFactory.thirdParty(platform.engineFactory(), config, platform.logger)
    }

    val apiCaller: ApiCaller by lazy { ApiCaller(backendHttpClient, json, sessionManager) }

    /** Email sign-in, device limit, guest login and logout (Phase 3). */
    val authRepository: AuthRepository by lazy {
        DefaultAuthRepository(apiCaller, json, platform.deviceInfo, ::encryptForTransport)
    }

    /**
     * An email-step controller with its own main-thread scope, for platforms without a lifecycle
     * scope (VPlayed): call `close()` when the screen goes away. AndroidJP builds controllers on
     * `viewModelScope` instead.
     */
    fun emailLoginController(config: AuthFlowConfig): EmailLoginController =
        EmailLoginController(authRepository, mainScope(), config)

    /** A code-step controller with its own main-thread scope (see [emailLoginController]). */
    fun otpController(email: String, initialDeviceLimit: DeviceLimit?, config: AuthFlowConfig): OtpController =
        OtpController(email, authRepository, initialDeviceLimit, mainScope(), config)

    /** [SessionManager.logout] with this graph's [authRepository]. */
    suspend fun logout(): LogoutResult = sessionManager.logout(authRepository)

    /** Public IP → geo token → location, into [geoStore] (Phase 4). */
    val geoRepository: GeoRepository by lazy {
        DefaultGeoRepository(apiCaller, thirdPartyHttpClient, json, geoStore)
    }

    /** Launch-time app settings (`mediaview/api/v1/common`). */
    val commonSettingsRepository: CommonSettingsRepository by lazy {
        DefaultCommonSettingsRepository(apiCaller, config.platform)
    }

    /** Maps content JSON for Home and Movies (one image table per app, the device language). */
    private val homeMapper: HomeMapper by lazy {
        HomeMapper(
            imageBaseUrl = config.imageBaseUrl,
            imagePolicy = HomeImagePolicy.forPlatform(config.platform),
            language = platform.deviceInfo::languageCode,
        )
    }

    /** Banners, rows, row items and channel follow (Phase 4). */
    val homeRepository: HomeRepository by lazy { DefaultHomeRepository(api = apiCaller, mapper = homeMapper) }

    /** The movie catalogue (VPlayed's Movies tab). */
    val moviesRepository: MoviesRepository by lazy { DefaultMoviesRepository(apiCaller, homeMapper) }

    /** A Home feed controller with its own main-thread scope (see [emailLoginController]). */
    fun homeFeedController(): HomeFeedController = HomeFeedController(homeRepository, mainScope())

    private fun mainScope() = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Encrypts a request payload with the build's transport key. Null when the build has no key
     * or encryption fails: the caller must not send the request.
     */
    fun encryptForTransport(plainJson: String): String? = transportCipher.encrypt(config.transportKey, plainJson)
}
