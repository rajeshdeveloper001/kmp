package com.vplayed.shared.geo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The location inside a geo token (Base64 of a JSON object). Every field is optional: the backend
 * sends what it knows (VPlayed `GeoInfo`, AndroidJP `GeoLocationDto`).
 */
data class GeoInfo(
    val country: String? = null,
    val countryCode: String? = null,
    val currency: String? = null,
    val region: String? = null,
    val regionName: String? = null,
    val city: String? = null,
    val timezone: String? = null,
)

/** Geo-restriction values the backend needs. [geoToken] is a credential: [toString] never prints it or the IP. */
data class GeoState(
    /** Public IP of the current network, or null before the first successful lookup. */
    val ip: String? = null,
    /** Geo access key from `common/geo-data/{ip}`, sent as `x-bearer-token`; null until fetched. */
    val geoToken: String? = null,
    /** Currency decoded from [geoToken]; [DEFAULT_CURRENCY] until a valid one is known. */
    val currency: String = DEFAULT_CURRENCY,
    /** The location decoded from [geoToken], or null when it is missing or undecodable. */
    val info: GeoInfo? = null,
) {
    override fun toString(): String =
        "GeoState(ip=${if (ip == null) "none" else "set"}, geoToken=${if (geoToken == null) "none" else "set"}, " +
            "currency=$currency, country=${info?.countryCode ?: "unknown"})"

    companion object {
        const val DEFAULT_CURRENCY = "INR"
    }
}

/**
 * Single source of truth for the public IP, geo token and currency (AndroidJP `GeoRestrictionStore`,
 * VPlayed `AppStartup.geoInfo` + `SessionStore.geoToken`). In memory only, as in both apps.
 *
 * Everyone reads [state]; only [GeoRepository] writes. Updates are atomic and ignore blank values,
 * so a failed or empty response never erases the last valid values.
 */
class GeoStore {
    private val mutableState = MutableStateFlow(GeoState())
    val state: StateFlow<GeoState> = mutableState.asStateFlow()

    /** The current values, typed for Swift (a `StateFlow`'s value reaches Swift untyped). */
    val current: GeoState get() = mutableState.value

    /** The current geo token, for request headers (read from any thread). */
    val geoToken: String? get() = mutableState.value.geoToken

    fun updateIp(ip: String) {
        if (ip.isBlank()) return
        mutableState.update { it.copy(ip = ip) }
    }

    /**
     * Saves a new [token] and the location decoded from it. The currency changes only when [info]
     * carries a non-blank one (AndroidJP rule); [GeoState.info] always describes the current token.
     */
    fun updateGeo(token: String, info: GeoInfo?) {
        if (token.isBlank()) return
        mutableState.update { current ->
            current.copy(
                geoToken = token,
                currency = info?.currency?.takeIf { it.isNotBlank() } ?: current.currency,
                info = info,
            )
        }
    }
}
