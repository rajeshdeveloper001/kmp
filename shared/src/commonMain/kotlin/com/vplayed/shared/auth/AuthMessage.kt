package com.vplayed.shared.auth

import com.vplayed.shared.core.error.AppError

/**
 * Something the sign-in screens tell the user. Shared code never holds user-facing text of its own:
 * it passes on the server's words when they are safe to show, else an [AuthMessageKey] that each app
 * turns into its own string (AndroidJP `strings.xml`, VPlayed `Localizable.xcstrings`).
 */
sealed interface AuthMessage {
    /** The server's explanation, already checked by [serverOr]. */
    data class Server(val text: String) : AuthMessage

    /** A message the app supplies the text for. */
    data class Localized(val key: AuthMessageKey) : AuthMessage

    companion object {
        private const val MAX_SERVER_MESSAGE_LENGTH = 200

        /** [serverText] when it is short plain text (no markup, stack traces or multi-line dumps), else [fallback]. */
        fun serverOr(serverText: String?, fallback: AuthMessageKey): AuthMessage =
            serverText?.trim()?.takeIf(::isDisplayable)?.let(::Server) ?: Localized(fallback)

        /** The app's own message for a failure (server texts of failures are not shown). */
        fun from(error: AppError): AuthMessage = Localized(
            when (error) {
                AppError.Network -> AuthMessageKey.ERROR_NETWORK
                AppError.Timeout -> AuthMessageKey.ERROR_TIMEOUT
                AppError.Unauthorized -> AuthMessageKey.ERROR_UNAUTHORIZED
                is AppError.Server -> AuthMessageKey.ERROR_SERVER
                AppError.Parsing -> AuthMessageKey.ERROR_PARSING
                is AppError.Configuration, is AppError.Unknown -> AuthMessageKey.ERROR_UNKNOWN
            },
        )

        private fun isDisplayable(text: String): Boolean =
            text.isNotEmpty() && text.length <= MAX_SERVER_MESSAGE_LENGTH && text.none { it == '<' || it == '>' || it == '\n' }
    }
}

/** The sign-in messages each app translates. */
enum class AuthMessageKey {
    /** The account is signed in on its maximum number of devices. */
    DEVICE_LIMIT,

    /** The backend refused to send a code, without a usable explanation. */
    LOGIN_FAILED,

    /** The backend refused a guest session, without a usable explanation. */
    GUEST_LOGIN_FAILED,

    /** A wrong or expired code. */
    OTP_INVALID,

    /** The account must be registered first (no registration screen yet). */
    REGISTRATION_REQUIRED,

    /** Signing a device out failed. */
    DEVICE_SIGN_OUT_FAILED,

    /** Success step, new account: profile setup is not built yet. (Not NEW_…: Objective-C reserves "new".) */
    PROFILE_SETUP_PENDING,

    /** Success step, several profiles: profile choice is not built yet; the default profile is used. */
    MULTI_PROFILE_PENDING,

    ERROR_NETWORK,
    ERROR_TIMEOUT,
    ERROR_UNAUTHORIZED,
    ERROR_SERVER,
    ERROR_PARSING,
    ERROR_UNKNOWN,
}
