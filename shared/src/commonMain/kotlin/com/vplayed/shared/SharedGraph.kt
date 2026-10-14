package com.vplayed.shared

import com.vplayed.shared.core.config.SharedConfig
import com.vplayed.shared.core.crypto.CryptoPrimitives
import com.vplayed.shared.core.crypto.TransportCipher
import com.vplayed.shared.core.crypto.platformCryptoPrimitives
import com.vplayed.shared.core.network.ApiCaller
import com.vplayed.shared.core.network.HttpClientFactory
import com.vplayed.shared.core.network.InMemoryRequestCredentials
import com.vplayed.shared.core.network.createPlatformHttpEngine
import com.vplayed.shared.core.platform.DeviceInfo
import com.vplayed.shared.core.platform.SharedLogger
import com.vplayed.shared.core.platform.platformLogger
import com.vplayed.shared.core.serialization.SharedJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
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
) {
    val json: Json = SharedJson

    val logger: SharedLogger get() = platform.logger

    /** Session and geo credentials for request headers (moves under the session manager in Phase 3). */
    val requestCredentials: InMemoryRequestCredentials = InMemoryRequestCredentials()

    val transportCipher: TransportCipher by lazy { TransportCipher(platform.cryptoPrimitives) }

    /** VPlayed backend client (headers, base URL, timeouts). */
    val backendHttpClient: HttpClient by lazy {
        HttpClientFactory.backend(platform.engineFactory(), config, platform.deviceInfo, requestCredentials, platform.logger)
    }

    /** Third-party client without VPlayed headers (public IP lookup). */
    val thirdPartyHttpClient: HttpClient by lazy {
        HttpClientFactory.thirdParty(platform.engineFactory(), config, platform.logger)
    }

    val apiCaller: ApiCaller by lazy { ApiCaller(backendHttpClient, json) }

    /**
     * Encrypts a request payload with the build's transport key. Null when the build has no key
     * or encryption fails: the caller must not send the request.
     */
    fun encryptForTransport(plainJson: String): String? = transportCipher.encrypt(config.transportKey, plainJson)
}
