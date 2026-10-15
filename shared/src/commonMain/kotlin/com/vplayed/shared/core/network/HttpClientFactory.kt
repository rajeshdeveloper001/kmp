package com.vplayed.shared.core.network

import com.vplayed.shared.core.config.SharedConfig
import com.vplayed.shared.core.platform.DeviceInfo
import com.vplayed.shared.core.platform.SharedLogger
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest

/** OkHttp on Android, NSURLSession (Darwin) on Apple platforms. Each client gets its own engine. */
expect fun createPlatformHttpEngine(): HttpClientEngine

/** Builds the two HTTP clients the shared layer uses. */
object HttpClientFactory {

    /**
     * Client for the VPlayed backend: base URL, timeouts, the common headers and (in debuggable
     * builds) one-line request logging. Status codes are never turned into exceptions
     * (`expectSuccess = false`): they carry meaning and repositories interpret them.
     */
    fun backend(
        engine: HttpClientEngine,
        config: SharedConfig,
        deviceInfo: DeviceInfo,
        credentials: RequestCredentials,
        logger: SharedLogger,
    ): HttpClient = HttpClient(engine) {
        defaultRequest { url(config.serverUrl) }
        install(VPlayedHeaders) {
            platform = config.platform
            appVersion = config.appVersion
            this.deviceInfo = deviceInfo
            this.credentials = credentials
        }
        common(config, logger)
    }

    /**
     * Client for third-party services (the public IP lookup): no VPlayed headers, so the device id,
     * session and geo token never leave for another host.
     */
    fun thirdParty(engine: HttpClientEngine, config: SharedConfig, logger: SharedLogger): HttpClient = HttpClient(engine) {
        common(config, logger)
    }

    private fun HttpClientConfig<*>.common(config: SharedConfig, logger: SharedLogger) {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = config.requestTimeoutMillis
            socketTimeoutMillis = config.requestTimeoutMillis
            connectTimeoutMillis = config.connectTimeoutMillis
        }
        if (config.loggingEnabled) {
            install(RequestLogging) {
                this.logger = logger
                this.redactSensitive = config.redactSensitive
            }
        }
    }
}
