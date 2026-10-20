package com.vplayed.shared.session

import com.vplayed.shared.core.error.AppError

/**
 * Exchanges a refresh token for a new access token (`auth/refresh-token`, the UIKit app's
 * `AuthClient.fetchNewAccesstoken`). The graph wires it to `DefaultAuthRepository`.
 */
fun interface AccessTokenRefresher {
    suspend fun refresh(refreshToken: String): TokenRefreshResult
}

sealed interface TokenRefreshResult {
    /** The server issued a new access token; the refresh token is kept (the backend does not rotate it). */
    data class Refreshed(val accessToken: String) : TokenRefreshResult {
        override fun toString(): String = "Refreshed(accessToken=***)"
    }

    /** The server refused the refresh token (or answered without a token): the session is over. */
    data object Rejected : TokenRefreshResult

    /** No usable answer (offline, timeout, server error): the session is kept. */
    data class Failed(val error: AppError) : TokenRefreshResult
}
