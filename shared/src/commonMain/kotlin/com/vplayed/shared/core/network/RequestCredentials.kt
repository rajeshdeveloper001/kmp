package com.vplayed.shared.core.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The signed-in (or guest) credentials sent with every backend request. Credentials: [toString] never prints them. */
data class AuthTokens(
    val accessToken: String,
    /** Sent as `profile_id` when present. */
    val profileId: String? = null,
) {
    override fun toString(): String = "AuthTokens(accessToken=***, profileId=${if (profileId == null) "none" else "set"})"
}

/** Where the header plugin reads credentials from. Read on every request, from any thread. */
interface RequestCredentials {
    val authTokens: AuthTokens?

    /** Geo access key, sent as `x-bearer-token`. */
    val geoToken: String?
}

/**
 * Thread-safe in-memory [RequestCredentials], for tests of the HTTP layer. The graph's credentials
 * come from its `SessionManager` and `GeoStore`.
 */
class InMemoryRequestCredentials : RequestCredentials {
    private val auth = MutableStateFlow<AuthTokens?>(null)
    private val geo = MutableStateFlow<String?>(null)

    val authTokensFlow: StateFlow<AuthTokens?> = auth.asStateFlow()

    override val authTokens: AuthTokens? get() = auth.value
    override val geoToken: String? get() = geo.value

    /** Null signs out: no `Authorization` / `profile_id` from the next request on. */
    fun setAuthTokens(tokens: AuthTokens?) {
        auth.value = tokens?.takeIf { it.accessToken.isNotBlank() }
    }

    /** A blank token is ignored, so a failed lookup never erases the last valid one (AndroidJP `GeoRestrictionStore` rule). */
    fun setGeoToken(token: String) {
        if (token.isNotBlank()) geo.value = token
    }
}
