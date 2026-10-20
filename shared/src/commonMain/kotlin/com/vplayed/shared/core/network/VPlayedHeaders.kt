package com.vplayed.shared.core.network

import com.vplayed.shared.core.config.ClientPlatform
import com.vplayed.shared.core.platform.DeviceInfo
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders

/** Header names the VPlayed backend reads (HTTP header names are case-insensitive). */
object VPlayedHeaderNames {
    const val REQUEST_TYPE = "X-Request-Type"
    const val DEVICE_TYPE = "device_type"
    const val DEVICE_ID = "device_id"
    const val APP_VERSION = "app_version"
    const val LANGUAGE = "X-Language-Code"
    const val PROFILE_ID = "profile_id"
    const val GEO_TOKEN = "x-bearer-token"

    /** Never printed by the request logger. */
    val SENSITIVE: Set<String> = setOf(HttpHeaders.Authorization, GEO_TOKEN)
}

class VPlayedHeadersConfig {
    lateinit var platform: ClientPlatform
    lateinit var appVersion: String
    lateinit var deviceInfo: DeviceInfo
    lateinit var credentials: RequestCredentials
}

private const val REQUEST_TYPE_MOBILE = "mobile"
private const val DEFAULT_LANGUAGE = "en"

/**
 * The header set both apps send (it replaced AndroidJP's `ApiHeadersInterceptor` and VPlayed's `RequestHeaders`):
 *
 * - always: `Accept`, `X-Request-Type: mobile`, `device_type`, `app_version`, `X-Language-Code`;
 * - `device_id` when the platform provides one;
 * - with a session: `Authorization: Bearer …` (unless [RequestOptions.sendAuthorization] is false) and
 *   `profile_id` (when known); nothing when signed out. [RequestOptions.sessionOverride] replaces the
 *   stored session's credentials for one request;
 * - `x-bearer-token` whenever a geo token is known, except on requests with
 *   [RequestOptions.sendGeoToken] = false. Never sent empty.
 *
 * Installed only on the VPlayed backend client; third-party calls (public IP lookup) get none of these.
 */
val VPlayedHeaders = createClientPlugin("VPlayedHeaders", ::VPlayedHeadersConfig) {
    val platform = pluginConfig.platform
    val appVersion = pluginConfig.appVersion
    val deviceInfo = pluginConfig.deviceInfo
    val credentials = pluginConfig.credentials

    onRequest { request, _ ->
        val options = request.attributes.getOrNull(RequestOptionsKey) ?: RequestOptions.Default
        with(request.headers) {
            set(HttpHeaders.Accept, ContentType.Application.Json.toString())
            set(VPlayedHeaderNames.REQUEST_TYPE, REQUEST_TYPE_MOBILE)
            set(VPlayedHeaderNames.DEVICE_TYPE, platform.headerValue)
            set(VPlayedHeaderNames.APP_VERSION, appVersion)
            set(VPlayedHeaderNames.LANGUAGE, deviceInfo.languageCode().trim().ifEmpty { DEFAULT_LANGUAGE })
            deviceInfo.deviceId?.takeIf { it.isNotBlank() }?.let { set(VPlayedHeaderNames.DEVICE_ID, it) }
            (options.sessionOverride ?: credentials.authTokens)?.let { tokens ->
                if (options.sendAuthorization) set(HttpHeaders.Authorization, "Bearer ${tokens.accessToken}")
                tokens.profileId?.takeIf { it.isNotBlank() }?.let { set(VPlayedHeaderNames.PROFILE_ID, it) }
            }
            val geoToken = credentials.geoToken
            if (options.sendGeoToken && !geoToken.isNullOrBlank()) set(VPlayedHeaderNames.GEO_TOKEN, geoToken)
        }
    }
}
