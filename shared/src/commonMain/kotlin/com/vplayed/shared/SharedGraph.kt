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
import com.vplayed.shared.auth.PhoneAuthRepository
import com.vplayed.shared.auth.PhoneLoginController
import com.vplayed.shared.auth.PhoneOtpSender
import com.vplayed.shared.auth.PendingRegistration
import com.vplayed.shared.auth.RegistrationController
import com.vplayed.shared.auth.RegistrationRepository
import com.vplayed.shared.auth.SocialAuthRepository
import com.vplayed.shared.auth.SocialCredential
import com.vplayed.shared.auth.SocialLoginController
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
import com.vplayed.shared.phone.CountryDialCodes
import com.vplayed.shared.phone.PhoneNumber
import com.vplayed.shared.profile.DefaultProfileRepository
import com.vplayed.shared.profile.FirstProfileChooser
import com.vplayed.shared.profile.ProfileRepository
import com.vplayed.shared.profile.SessionProfileChooser
import com.vplayed.shared.session.AccessTokenRefresher
import com.vplayed.shared.session.GuestSessionKeeper
import com.vplayed.shared.session.SessionCredentials
import com.vplayed.shared.session.SessionManager
import com.vplayed.shared.settings.CommonSettingsRepository
import com.vplayed.shared.settings.CommonSettingsStore
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

    /** Backend requests; a 401 tries one token refresh (`auth/refresh-token`) before it ends the session. */
    val apiCaller: ApiCaller by lazy {
        ApiCaller(backendHttpClient, json, sessionManager, AccessTokenRefresher { defaultAuth.refreshAccessToken(it) })
    }

    private val defaultAuth: DefaultAuthRepository by lazy {
        DefaultAuthRepository(apiCaller, json, platform.deviceInfo, ::encryptForTransport, config.platform)
    }

    /** Email sign-in, device limit, guest login and logout (Phase 3). */
    val authRepository: AuthRepository get() = defaultAuth

    /** Phone sign-in (`auth/phone-login`); the SMS itself comes from the app's [PhoneOtpSender]. */
    val phoneAuthRepository: PhoneAuthRepository get() = defaultAuth

    /** Google / Apple sign-in (`auth/login`). */
    val socialAuthRepository: SocialAuthRepository get() = defaultAuth

    /** Sign-up of new accounts (`auth/register`, `auth/update-profile`). */
    val registrationRepository: RegistrationRepository get() = defaultAuth

    /** The account's viewing profiles (`muti-profile/list`). */
    val profileRepository: ProfileRepository by lazy { DefaultProfileRepository(apiCaller) }

    /**
     * Picks the profile of each new sign-in before the app stores it: the account's first profile
     * (no profile picker yet). The sign-in controllers built here use it; AndroidJP passes it to the
     * ones it builds.
     */
    val profileChooser: SessionProfileChooser by lazy { FirstProfileChooser(profileRepository, platform.logger) }

    /**
     * An email-step controller with its own main-thread scope, for platforms without a lifecycle
     * scope (VPlayed): call `close()` when the screen goes away. AndroidJP builds controllers on
     * `viewModelScope` instead.
     */
    fun emailLoginController(config: AuthFlowConfig): EmailLoginController =
        EmailLoginController(authRepository, mainScope(), config)

    /** A code-step controller with its own main-thread scope (see [emailLoginController]). */
    fun otpController(email: String, initialDeviceLimit: DeviceLimit?, config: AuthFlowConfig): OtpController =
        OtpController(email, authRepository, initialDeviceLimit, mainScope(), config, profileChooser)

    /**
     * A mobile-number step with its own main-thread scope (see [emailLoginController]). The country
     * starts at [regionCode] (e.g. the device region) when the picker offers it, else India.
     */
    fun phoneLoginController(config: AuthFlowConfig, regionCode: String?): PhoneLoginController =
        PhoneLoginController(phoneAuthRepository, mainScope(), config, CountryDialCodes.forRegion(regionCode) ?: CountryDialCodes.DEFAULT)

    /** A Google / Apple step with its own main-thread scope (see [emailLoginController]). */
    fun socialLoginController(config: AuthFlowConfig): SocialLoginController =
        SocialLoginController(socialAuthRepository, mainScope(), config, profileChooser)

    /** A phone code-step controller with its own main-thread scope; it texts the first code through [sender]. */
    fun phoneOtpController(
        phone: PhoneNumber,
        sender: PhoneOtpSender,
        initialDeviceLimit: DeviceLimit?,
        config: AuthFlowConfig,
    ): OtpController =
        OtpController.phone(phone, phoneAuthRepository, authRepository, sender, initialDeviceLimit, mainScope(), config, profileChooser)

    /** The device step of a Google / Apple sign-in at its device limit, with its own main-thread scope. */
    fun socialDevicesController(credential: SocialCredential, limit: DeviceLimit, config: AuthFlowConfig): OtpController =
        OtpController.socialDevices(credential, socialAuthRepository, authRepository, limit, mainScope(), config, profileChooser)

    /** The sign-up form for [registration] (from `OtpEffect.RegistrationNeeded`), with its own main-thread scope. */
    fun registrationController(registration: PendingRegistration, config: AuthFlowConfig): RegistrationController =
        RegistrationController(registration, registrationRepository, mainScope(), config, profileChooser)

    /** [SessionManager.logout] with this graph's [authRepository]. */
    suspend fun logout(): LogoutResult = sessionManager.logout(authRepository)

    /** Public IP → geo token → location, into [geoStore] (Phase 4). */
    val geoRepository: GeoRepository by lazy {
        DefaultGeoRepository(apiCaller, thirdPartyHttpClient, json, geoStore)
    }

    /** The last loaded common settings. */
    val commonSettingsStore: CommonSettingsStore = CommonSettingsStore()

    /** Launch-time app settings (`mediaview/api/v1/common`), kept in [commonSettingsStore]. */
    val commonSettingsRepository: CommonSettingsRepository by lazy {
        DefaultCommonSettingsRepository(apiCaller, config.platform, commonSettingsStore)
    }

    /** The automatic guest session; each app calls `start()` once at launch. */
    val guestSessionKeeper: GuestSessionKeeper by lazy {
        GuestSessionKeeper(sessionManager, authRepository, commonSettingsStore, backgroundScope, platform.logger)
    }

    /** Work that lives as long as the graph (one per process). */
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
