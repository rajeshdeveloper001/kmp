package com.vplayed.shared.auth

import com.vplayed.shared.core.error.AppError
import com.vplayed.shared.core.error.DataResult
import com.vplayed.shared.core.error.HTTP_UNAUTHORIZED
import com.vplayed.shared.core.network.ApiCaller
import com.vplayed.shared.core.network.RequestOptions
import com.vplayed.shared.core.network.VPlayedResponse
import com.vplayed.shared.core.platform.DeviceInfo
import com.vplayed.shared.core.serialization.lenientBool
import com.vplayed.shared.core.serialization.lenientInt
import com.vplayed.shared.core.serialization.lenientLong
import com.vplayed.shared.core.serialization.lenientString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * The VPlayed sign-in endpoints (AndroidJP `docs/api/auth-api.md`): email code, device limit,
 * guest session and logout. Every call returns a result type and none throws.
 *
 * How answers are read (decisions recorded in `KMP_PLANNING/KMP_ARCHITECTURE_ANALYSIS.md` §13):
 * - **D1** — a non-2xx HTTP status is always a failure. Within 2xx the envelope's `statusCode`
 *   decides when present, else the HTTP status. A 2xx answer with `"error": true` is a refusal.
 * - **D2** — `email-login` 203 means a code was sent to a new account: go to the code step.
 * - **D3** — `email-login` 202 is the device-limit answer, with the device list.
 * - **D4** — `verifyEmailOtp` 200 or 203 with an access token signs in (203 = new account);
 *   203 without one means "register first".
 * - **D5** — [continueAfterDeviceSignOut] signs in **without a code**, as the previous Android
 *   app did. Only AndroidJP calls it; VPlayed keeps it off until the backend confirms it is safe.
 *
 * Request bodies hold emails, codes and tokens, so every call is [RequestOptions.Sensitive] and
 * nothing about them is logged. The session is returned, not stored: the apps own it until Phase 5.
 */
class AuthRepository internal constructor(
    private val api: ApiCaller,
    private val json: Json,
    private val deviceInfo: DeviceInfo,
    /** The graph's transport encryption; null when the build has no key. */
    private val encrypt: (String) -> String?,
) {

    /** Requests (or re-requests) a login code for [email] (trimmed here). */
    suspend fun requestEmailLogin(email: String): EmailLoginResult {
        val body = encryptedBody { put("email", email.trim()) }
            ?: return EmailLoginResult.Failure(AppError.Configuration(MISSING_KEY))
        return when (val result = api.postJson(EMAIL_LOGIN, body, RequestOptions.Sensitive)) {
            is DataResult.Failure -> EmailLoginResult.Failure(result.error)
            is DataResult.Success -> result.data.toEmailLoginResult()
        }
    }

    /** Verifies [code] for [email]. On success the caller stores [OtpVerificationResult.Verified.session]. */
    suspend fun verifyEmailOtp(email: String, code: String): OtpVerificationResult {
        val address = email.trim()
        val body = encryptedBody {
            put("email", address)
            put("otp", code)
            put("deviceDetail", deviceInfo.deviceName)
        } ?: return OtpVerificationResult.Failure(AppError.Configuration(MISSING_KEY))
        return when (val result = api.postJson(VERIFY_EMAIL_OTP, body, RequestOptions.Sensitive)) {
            is DataResult.Failure -> OtpVerificationResult.Failure(result.error)
            is DataResult.Success -> result.data.toVerification(address)
        }
    }

    /** Signs a device out of the account ([all] = every device, using [key] of the first). */
    suspend fun signOutDevice(key: DeviceSignOutKey, all: Boolean): SignOutDeviceResult {
        val fields = buildList {
            add("username" to key.username)
            key.accessToken?.let { add("access_token" to it) }
            add("preference" to if (all) PREFERENCE_ALL else PREFERENCE_ONE)
        }
        return when (val result = api.postForm(LOGOUT_DEVICE, fields, RequestOptions.Sensitive)) {
            is DataResult.Failure -> SignOutDeviceResult.Failure(result.error)
            is DataResult.Success -> result.data.toSignOutResult()
        }
    }

    /**
     * Completes the login for [email] after devices were signed out — **without a code** (the
     * previous Android app's `verifyEmailLogin`; decision D5, pending the backend's security answer).
     */
    suspend fun continueAfterDeviceSignOut(email: String): OtpVerificationResult {
        val address = email.trim()
        val fields = listOf(
            "is_email_verified" to "1",
            "email" to address,
            "deviceDetail" to deviceInfo.deviceName,
            "browsername" to "",
        )
        return when (val result = api.postForm(VERIFY_EMAIL_LOGIN, fields, RequestOptions.Sensitive)) {
            is DataResult.Failure -> OtpVerificationResult.Failure(result.error)
            is DataResult.Success -> result.data.toVerification(address)
        }
    }

    /** Starts a guest session for this device (`{"uuid": device id}`, not encrypted). */
    suspend fun guestLogin(): GuestLoginResult {
        val deviceId = deviceInfo.deviceId?.takeIf { it.isNotBlank() }
            ?: return GuestLoginResult.Failure(AppError.Configuration("No device id for a guest session"))
        val body = buildJsonObject { put("uuid", deviceId) }
        return when (val result = api.postJson(GUEST_LOGIN, body, RequestOptions.Sensitive)) {
            is DataResult.Failure -> GuestLoginResult.Failure(result.error)
            is DataResult.Success -> result.data.toGuestResult()
        }
    }

    /** Ends the current session on the server (the `Authorization` header says which). */
    suspend fun logout(): LogoutResult =
        when (val result = api.postJson(LOGOUT, JsonObject(emptyMap()), RequestOptions.Sensitive)) {
            is DataResult.Failure -> LogoutResult.Failure(result.error)
            is DataResult.Success -> result.data.toLogoutResult()
        }

    private fun encryptedBody(fields: JsonObjectBuilder.() -> Unit): JsonElement? {
        val plain = json.encodeToString(JsonElement.serializer(), buildJsonObject(fields))
        val cipherText = encrypt(plain) ?: return null
        return buildJsonObject { put("data", cipherText) }
    }

    // region Answers

    private fun VPlayedResponse.toEmailLoginResult(): EmailLoginResult {
        if (isHttpSuccess && envelope == null) return EmailLoginResult.Failure(AppError.Parsing)
        val status = authStatus
        return when {
            // 202 and 203 are decided by the status alone, as in the previous apps (`error` is not read).
            status == STATUS_DEVICE_LIMIT -> EmailLoginResult.DeviceLimitReached(message, user()?.toDeviceLimit())
            status == STATUS_NEW_USER -> EmailLoginResult.CodeSent(message, isNewUser = true)
            // A 200 that reports an API error is not "code sent".
            status == STATUS_OK && !reportsError -> EmailLoginResult.CodeSent(message, isNewUser = false)
            else -> refusal(EmailLoginResult::Rejected, EmailLoginResult::Failure)
        }
    }

    /** Shared by `verifyEmailOtp` and `verifyEmailLogin`: same response contract. */
    private fun VPlayedResponse.toVerification(requestedEmail: String): OtpVerificationResult {
        if (isHttpSuccess && envelope == null) return OtpVerificationResult.Failure(AppError.Parsing)
        val status = authStatus
        return when {
            status == STATUS_DEVICE_LIMIT -> OtpVerificationResult.DeviceLimitReached(message, user()?.toDeviceLimit())
            // A 200 with `"error": true` is a wrong or expired code; 203 is decided by the status.
            status == STATUS_NEW_USER || (status == STATUS_OK && !reportsError) -> {
                val user = user()
                val session = user?.toSession(requestedEmail, guest = false)
                when {
                    session != null -> OtpVerificationResult.Verified(
                        session = session,
                        isNewUser = status == STATUS_NEW_USER || user["isNewUsers"].lenientBool() == true || session.firstName == null,
                        hasMultipleProfiles = (user["profile_count"].lenientInt() ?: 0) > 1,
                    )
                    status == STATUS_NEW_USER -> OtpVerificationResult.RegistrationRequired(message)
                    // A 200 must carry a session; anything else is unreadable, not a login.
                    else -> OtpVerificationResult.Failure(AppError.Parsing)
                }
            }
            else -> refusal(OtpVerificationResult::Rejected, OtpVerificationResult::Failure)
        }
    }

    private fun VPlayedResponse.toSignOutResult(): SignOutDeviceResult {
        if (!isHttpSuccess) return refusal(SignOutDeviceResult::Rejected, SignOutDeviceResult::Failure)
        val envelope = envelope ?: return SignOutDeviceResult.Failure(AppError.Parsing)
        // The previous app accepts only an explicit `error: false`.
        return if (envelope.error == false) SignOutDeviceResult.Success else SignOutDeviceResult.Rejected(message)
    }

    private fun VPlayedResponse.toGuestResult(): GuestLoginResult {
        if (isHttpSuccess && envelope == null) return GuestLoginResult.Failure(AppError.Parsing)
        if (authStatus != STATUS_OK || reportsError) return refusal(GuestLoginResult::Rejected, GuestLoginResult::Failure)
        // This endpoint only issues guest sessions, whatever `is_guest_user` says.
        val session = user()?.toSession(requestedEmail = null, guest = true)
            ?: return GuestLoginResult.Failure(AppError.Parsing)
        return GuestLoginResult.SignedIn(session)
    }

    private fun VPlayedResponse.toLogoutResult(): LogoutResult {
        val status = authStatus
        if (status in SESSION_ENDED || httpStatus in SESSION_ENDED) return LogoutResult.SessionAlreadyEnded
        if (isHttpSuccess && envelope == null) return LogoutResult.Failure(AppError.Parsing)
        // VPlayed's UIKit app accepts 200–203 here and does not read `error`.
        if (status in LOGOUT_ACCEPTED) return LogoutResult.LoggedOut
        return refusal(LogoutResult::Rejected, LogoutResult::Failure)
    }

    // endregion

    // region Mapping

    private fun VPlayedResponse.user(): JsonObject? = envelope?.response as? JsonObject

    private fun JsonObject.toSession(requestedEmail: String?, guest: Boolean): AuthSession? {
        val token = this["access_token"].lenientString() ?: return null
        val isGuest = guest || this["is_guest_user"].lenientBool() == true
        val uniqueUserId = this["unique_user_id"].lenientLong()
        return AuthSession(
            accessToken = token,
            refreshToken = this["refresh_token"].lenientString(),
            userId = this["_id"].lenientString(),
            // Guests are identified by `guest_user_id`, falling back to `unique_user_id`.
            uniqueUserId = if (isGuest) this["guest_user_id"].lenientLong() ?: uniqueUserId else uniqueUserId,
            email = this["email"].lenientString() ?: requestedEmail?.takeIf { it.isNotEmpty() },
            phone = this["phone"].lenientString(),
            firstName = this["first_name"].lenientString(),
            profileId = (this["default_profile"] as? JsonObject)?.get("_id").lenientString(),
            isGuest = isGuest,
        )
    }

    private fun JsonObject.toDeviceLimit(): DeviceLimit? {
        val devices = (this["get_devices"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonObject)?.toSignedInDevice() }
        if (devices.isEmpty()) return null
        return DeviceLimit(devices, maxDevices = this["device_limit"].lenientInt()?.takeIf { it > 0 })
    }

    private fun JsonObject.toSignedInDevice(): SignedInDevice? {
        val username = this["username"].lenientString() ?: return null
        val type = this["device_type"].lenientString()
        return SignedInDevice(
            name = this["device_name"].lenientString() ?: type.orEmpty(),
            kind = deviceKindOf(type),
            detail = this["browser_name"].lenientString(),
            isCurrent = this["current_device"].lenientBool() == true,
            // Sent back exactly as received.
            signOutKey = DeviceSignOutKey(username, (this["access_token"] as? JsonPrimitive)?.contentOrNull),
        )
    }

    // endregion

    companion object {
        internal const val EMAIL_LOGIN = "users/api/v1/auth/email-login"
        internal const val VERIFY_EMAIL_OTP = "users/api/v1/auth/verifyEmailOtp"
        internal const val VERIFY_EMAIL_LOGIN = "users/api/v1/auth/verifyEmailLogin"
        internal const val LOGOUT_DEVICE = "users/api/v1/auth/logout-device"
        internal const val GUEST_LOGIN = "users/api/v1/auth/generate"
        internal const val LOGOUT = "users/api/v1/auth/logout"

        private const val STATUS_OK = 200
        private const val STATUS_DEVICE_LIMIT = 202
        private const val STATUS_NEW_USER = 203
        private val SESSION_ENDED = setOf(HTTP_UNAUTHORIZED, 403)
        private val LOGOUT_ACCEPTED = 200..203
        private const val PREFERENCE_ONE = "1"
        private const val PREFERENCE_ALL = "all"
        private const val MISSING_KEY = "Request encryption is not configured"

        /** The backend's `device_type` values are not documented; match common names, else a generic icon. */
        fun deviceKindOf(type: String?): DeviceKind {
            val value = type?.lowercase().orEmpty()
            return when {
                "tv" in value -> DeviceKind.TV
                listOf("android", "ios", "iphone", "mobile", "phone", "tablet").any { it in value } -> DeviceKind.PHONE
                listOf("web", "browser", "desktop", "windows", "mac", "linux").any { it in value } -> DeviceKind.DESKTOP
                else -> DeviceKind.OTHER
            }
        }
    }
}

/**
 * The status that decides an auth answer (decision D1): the HTTP status when it is not 2xx,
 * otherwise the envelope's `statusCode` when present, else the HTTP status.
 */
internal val VPlayedResponse.authStatus: Int
    get() = if (isHttpSuccess) effectiveStatusCode else httpStatus

/**
 * An answer outside the endpoint's success contract: a refusal the user can read, or a failure.
 * A 2xx without an error flag is never shown as a refusal (it is not part of the contract).
 */
internal inline fun <R> VPlayedResponse.refusal(rejected: (String?) -> R, failed: (AppError) -> R): R {
    val status = authStatus
    return when {
        reportsError -> rejected(message)
        status in 200..299 -> failed(AppError.Server(status, message))
        message != null -> rejected(message)
        status == HTTP_UNAUTHORIZED -> failed(AppError.Unauthorized)
        else -> failed(AppError.Server(status))
    }
}
