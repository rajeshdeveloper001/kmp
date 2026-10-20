package com.vplayed.shared.auth

import com.vplayed.shared.core.error.AppError
import com.vplayed.shared.core.network.AuthTokens
import com.vplayed.shared.phone.PhoneNumber

/**
 * A signed-in or guest session returned by a login call. The app hands it to
 * `SessionManager.signIn` when its flow is done, which stores it (`SessionCodec` format).
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

    /** What the request headers send for this session. */
    internal val requestTokens: AuthTokens get() = AuthTokens(accessToken, profileId)
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

/**
 * Outcome of verifying a login code, of continuing after the device-limit step, or of any other
 * login that returns a session (`auth/phone-login` with "1", `auth/login`).
 */
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
        /**
         * The account must finish sign-up before [session] is used (UIKit `RegisterViewController`):
         * an email account with no phone and no linked Google / Apple / Facebook id, or `phone-login`
         * 203. The session is not stored until then.
         */
        val needsRegistration: Boolean = false,
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

/** Social sign-in providers the app offers (`login_type` of `auth/login`). Facebook is hidden in the UIKit app. */
enum class SocialProvider(val loginType: String) {
    GOOGLE("google"),
    APPLE("apple"),
}

/**
 * What a platform sign-in SDK returned, sent to `auth/login` (the UIKit app's `LoginRequestModel`).
 * [token] goes as `id_token`: Google's OAuth access token on iOS (as the UIKit app), its ID token on
 * Android; Apple's identity token. Credentials and personal data: [toString] never prints them.
 */
data class SocialCredential(
    val provider: SocialProvider,
    /** `social_user_id`: Google's user ID, Apple's `user`. */
    val socialUserId: String,
    val token: String,
    val email: String?,
    val name: String?,
    /** `profile_picture`; Google only. */
    val pictureUrl: String?,
) {
    override fun toString(): String = "SocialCredential(provider=$provider, ***)"
}

/** Outcome of `auth/phone-login` with `is_phone_verified` "0": may the user go on to the code step? */
sealed interface PhoneLoginResult {
    /** Status 200 (existing account) or 203 (new account): send the code (by SMS, from the app). */
    data class Accepted(val message: String?, val isNewUser: Boolean) : PhoneLoginResult

    /** Status 202: the account is signed in on its maximum number of devices. */
    data class DeviceLimitReached(val message: String?, val limit: DeviceLimit?) : PhoneLoginResult

    data class Rejected(val message: String?) : PhoneLoginResult

    data class Failure(val error: AppError) : PhoneLoginResult
}

/** Which sign-up form a new account fills in (UIKit `RegisterViewController`, by `otpType`). */
enum class RegistrationKind {
    /** Signed in with an email code: asks for a name and a mobile number (`auth/update-profile`). */
    EMAIL_ACCOUNT,

    /** Signed in with a phone code: asks for a name and an email address (`auth/register`). */
    PHONE_ACCOUNT,
}

/**
 * A sign-in that must finish sign-up first. [session] (when the backend issued one) is not stored
 * until sign-up succeeds, so leaving the form leaves the user signed out of this account, as the
 * UIKit app does. Holds credentials: [toString] never prints them.
 */
data class PendingRegistration(
    val kind: RegistrationKind,
    val session: AuthSession?,
    /** The email signed in with ([RegistrationKind.EMAIL_ACCOUNT]); shown read-only. */
    val email: String?,
    /** The verified number ([RegistrationKind.PHONE_ACCOUNT]); shown read-only. */
    val phone: PhoneNumber?,
) {
    override fun toString(): String = "PendingRegistration(kind=$kind, ***)"
}

/** What sign-up needs from the form. Personal data: [toString] never prints it. */
data class RegistrationDetails(
    val name: String,
    /** The email: typed ([RegistrationKind.PHONE_ACCOUNT]) or signed in with. */
    val email: String?,
    /** The mobile number: typed ([RegistrationKind.EMAIL_ACCOUNT]) or verified. */
    val phone: PhoneNumber?,
    /** ISO region of [phone]'s country (`iso` / `country_iso_code`), e.g. "IN". */
    val regionCode: String?,
    /** [RegistrationKind.EMAIL_ACCOUNT]: the referral code, when one was typed. */
    val referralCode: String?,
) {
    override fun toString(): String = "RegistrationDetails(***)"
}

/** Outcome of sign-up (`auth/register` or `auth/update-profile`). */
sealed interface RegistrationResult {
    /** Done: the caller stores [session]. */
    data class Registered(val session: AuthSession) : RegistrationResult

    data class Rejected(val message: String?) : RegistrationResult

    data class Failure(val error: AppError) : RegistrationResult
}
