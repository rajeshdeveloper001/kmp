package com.vplayed.shared.auth

import com.vplayed.shared.core.error.AppError

/**
 * A signed-in or guest session returned by a login call. The apps store it (AndroidJP
 * `SessionRepository`, VPlayed `SessionStore`) until the shared session manager exists (Phase 5).
 * The tokens are credentials: [toString] never prints them.
 */
data class AuthSession(
    val accessToken: String,
    val refreshToken: String?,
    /** `_id`; null when the backend sends none. */
    val userId: String?,
    /** `unique_user_id` (guests: `guest_user_id`, else `unique_user_id`). */
    val uniqueUserId: Long?,
    /** The account email, else the email the user signed in with; null for guests without one. */
    val email: String?,
    val phone: String?,
    val firstName: String?,
    /** `default_profile._id`, sent as `profile_id`. */
    val profileId: String?,
    val isGuest: Boolean,
) {
    override fun toString(): String =
        "AuthSession(userId=${if (userId == null) "none" else "set"}, isGuest=$isGuest, tokens=***)"
}

/** Kind of device shown in the device-limit list (icon only). */
enum class DeviceKind { DESKTOP, PHONE, TV, OTHER }

/** What is needed to sign one device out. Credentials of another device: never shown or logged. */
data class DeviceSignOutKey(val username: String, val accessToken: String?) {
    override fun toString(): String = "DeviceSignOutKey(***)"
}

data class SignedInDevice(
    val name: String,
    val kind: DeviceKind,
    /** Browser or app name, when the backend gives one. */
    val detail: String?,
    val isCurrent: Boolean,
    val signOutKey: DeviceSignOutKey,
)

/** The devices an account is signed in on when its limit is reached. */
data class DeviceLimit(val devices: List<SignedInDevice>, val maxDevices: Int?)

/** Outcome of asking for an email login code (`auth/email-login`). */
sealed interface EmailLoginResult {
    /**
     * Status 200 (existing account) or 203 (new account): a code was sent to the email. A new
     * account also goes to the code step (decision D2, AndroidJP's behaviour).
     */
    data class CodeSent(val message: String?, val isNewUser: Boolean) : EmailLoginResult

    /** Status 202: the account is signed in on its maximum number of devices (decision D3). */
    data class DeviceLimitReached(val message: String?, val limit: DeviceLimit?) : EmailLoginResult

    /** The backend refused the request and may have explained why in [message]. */
    data class Rejected(val message: String?) : EmailLoginResult

    /** No usable answer (network, server error without message, unreadable response, no transport key). */
    data class Failure(val error: AppError) : EmailLoginResult
}

/** Outcome of verifying a login code, or of continuing after the device-limit step. */
sealed interface OtpVerificationResult {
    /**
     * Signed in. The caller stores [session]. Status 203 with a session is a new account
     * (decision D4, AndroidJP's behaviour); [isNewUser] is also set by `isNewUsers` or a missing
     * first name.
     */
    data class Verified(
        val session: AuthSession,
        val isNewUser: Boolean,
        val hasMultipleProfiles: Boolean,
    ) : OtpVerificationResult

    /** Status 203 without a session: the account must be registered first. */
    data class RegistrationRequired(val message: String?) : OtpVerificationResult

    /** Status 202: the account is signed in on its maximum number of devices. */
    data class DeviceLimitReached(val message: String?, val limit: DeviceLimit?) : OtpVerificationResult

    /** The backend refused the code (invalid, expired, …) and may say why in [message]. */
    data class Rejected(val message: String?) : OtpVerificationResult

    data class Failure(val error: AppError) : OtpVerificationResult
}

sealed interface SignOutDeviceResult {
    data object Success : SignOutDeviceResult

    data class Rejected(val message: String?) : SignOutDeviceResult

    data class Failure(val error: AppError) : SignOutDeviceResult
}

/** Outcome of `auth/generate` (guest session for this device). */
sealed interface GuestLoginResult {
    data class SignedIn(val session: AuthSession) : GuestLoginResult

    data class Rejected(val message: String?) : GuestLoginResult

    data class Failure(val error: AppError) : GuestLoginResult
}

/** Outcome of `auth/logout`. The caller forgets the session on [LoggedOut] and [SessionAlreadyEnded]. */
sealed interface LogoutResult {
    data object LoggedOut : LogoutResult

    /** 401 / 403: the server no longer knows the token, so the session is gone anyway. */
    data object SessionAlreadyEnded : LogoutResult

    data class Rejected(val message: String?) : LogoutResult

    data class Failure(val error: AppError) : LogoutResult
}
