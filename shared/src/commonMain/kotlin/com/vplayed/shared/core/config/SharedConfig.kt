package com.vplayed.shared.core.config

/** Backend environment a build talks to. Chosen by the app's build configuration, never at runtime. */
enum class ServerEnvironment(val id: String) {
    DEVELOPMENT("dev"),
    QA("qa"),
    STAGING("stage"),
    DEMO("demo"),
    PRODUCTION("production"),
    ;

    val isProduction: Boolean get() = this == PRODUCTION

    companion object {
        /** Same ids as AndroidJP's `AppEnvironment`; unknown ids are a build error, so this throws. */
        fun fromId(id: String): ServerEnvironment =
            entries.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Unknown environment id '$id'")
    }
}

/** The app talking to the backend, sent as the `device_type` header. */
enum class ClientPlatform(val headerValue: String) {
    ANDROID("android"),

    /** iPhone, iPad, and the iPad app on Mac and Apple Vision Pro all identify as `ios` (current iOS app behaviour). */
    IOS("ios"),
}

/**
 * Build-time configuration of the shared layer. Each app builds it from its own source of truth
 * (AndroidJP: `AppConfig`; VPlayed: `APIEnvironment` + build settings), so neither app reads the
 * other's configuration.
 */
class SharedConfig(
    val environment: ServerEnvironment,
    /** API base URL, `https://…/` with a trailing slash (relative endpoint paths are appended). */
    val serverUrl: String,
    /** Base for relative image paths returned by the API, with a trailing slash. */
    val imageBaseUrl: String,
    /** Request logging; defaults to true in non-production builds (dev, qa, stage, demo). */
    val loggingEnabled: Boolean = !environment.isProduction,
    /** Sent as `app_version`. */
    val appVersion: String,
    val platform: ClientPlatform,
    /** Passphrase for request-payload encryption. Empty when the build has none. Secret: never logged. */
    val transportKey: String = "",
    val requestTimeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MILLIS,
    val connectTimeoutMillis: Long = DEFAULT_CONNECT_TIMEOUT_MILLIS,
) {
    init {
        listOf(serverUrl, imageBaseUrl).forEach { url ->
            require(url.startsWith("https://") && url.endsWith("/")) { "URLs must use https and end with '/': $url" }
        }
        require(requestTimeoutMillis > 0 && connectTimeoutMillis > 0) { "Timeouts must be positive" }
    }

    val hasTransportKey: Boolean get() = transportKey.isNotEmpty()

    // Keeps the secret out of logs and crash reports.
    override fun toString(): String =
        "SharedConfig(environment=$environment, serverUrl=$serverUrl, imageBaseUrl=$imageBaseUrl, " +
            "loggingEnabled=$loggingEnabled, appVersion=$appVersion, platform=$platform, " +
            "transportKey=${if (hasTransportKey) "***" else "unset"})"

    companion object {
        /** VPlayed's `URLSession` request timeout (30 s). */
        const val DEFAULT_REQUEST_TIMEOUT_MILLIS = 30_000L
        const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 15_000L
    }
}
