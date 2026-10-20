package com.vplayed.shared.auth

import com.vplayed.shared.core.config.ClientPlatform
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
import com.vplayed.shared.phone.PhoneNumber
import com.vplayed.shared.session.TokenRefreshResult
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
 * The VPlayed sign-in endpoints. Every call returns a result type and none throws. The apps get
 * [DefaultAuthRepository] from `SharedGraph.authRepository`; tests and the Android instrumented
 * tests use fakes.
 */
interface AuthRepository {
    /** Requests (or re-requests) a login code for [email] (trimmed by the implementation). */
    suspend fun requestEmailLogin(email: String): EmailLoginResult

    /** Verifies [code] for [email]. On success the caller stores [OtpVerificationResult.Verified.session]. */
    suspend fun verifyEmailOtp(email: String, code: String): OtpVerificationResult

    /** Signs a device out of the account ([all] = every device, using [key] of the first). */
    suspend fun signOutDevice(key: DeviceSignOutKey, all: Boolean): SignOutDeviceResult

    /** Completes the login for [email] after devices were signed out, **without a code** (decision D5). */
    suspend fun continueAfterDeviceSignOut(email: String): OtpVerificationResult

    /** Starts a guest session for this device. */
    suspend fun guestLogin(): GuestLoginResult

    /** Ends the current session on the server. */
    suspend fun logout(): LogoutResult
}

/**
 * [AuthRepository] against the VPlayed backend (AndroidJP `docs/api/auth-api.md`).
 *
 * How answers are read (decisions recorded in `KMP_PLANNING/KMP_ARCHITECTURE_ANALYSIS.md` §5):
 * - **D1** — a non-2xx HTTP status is always a failure. Within 2xx the envelope's `statusCode`
 *   decides when present, else the HTTP status. A 2xx answer with `"error": true` is a refusal.
 * - **D2** — `email-login` 203 means a code was sent to a new account: go to the code step.
 * - **D3** — `email-login` 202 is the device-limit answer, with the device list.
 * - **D4** — `verifyEmailOtp` 200 or 203 with an access token signs in (203 = new account);
 *   203 without one means "register first".
 * - **D5** — [continueAfterDeviceSignOut] signs in **without a code**, as the previous Android
 *   app did. Only AndroidJP calls it; VPlayed keeps it off until the backend confirms it is safe.
 *
 * It also serves phone sign-in ([PhoneAuthRepository], `auth/phone-login`), Google / Apple sign-in
 * ([SocialAuthRepository], `auth/login`), sign-up of new accounts ([RegistrationRepository],
 * `auth/register` / `auth/update-profile`) and the token refresh the graph's `ApiCaller` uses. Those
 * bodies are plain JSON, as in the UIKit app.
 *
 * Request bodies hold emails, codes and tokens, so every call is [RequestOptions.Sensitive] and
 * nothing about them is logged. The session is returned, not stored: callers hand it to
 * `SessionManager.signIn`; [logout] does not end it (`SharedGraph.logout` does).
 */
class DefaultAuthRepository internal constructor(
    private val api: ApiCaller,
    private val json: Json,
    private val deviceInfo: DeviceInfo,
    /** The graph's transport encryption; null when the build has no key. */
    private val encrypt: (String) -> String?,
    /** Decides `device_type` in the social login body. */
    private val platform: ClientPlatform = ClientPlatform.ANDROID,
) : AuthRepository, PhoneAuthRepository, SocialAuthRepository, RegistrationRepository {

    /** Requests (or re-requests) a login code for [email] (trimmed here). */
    override suspend fun requestEmailLogin(email: String): EmailLoginResult {
        val body = encryptedBody { put("email", email.trim()) }
            ?: return EmailLoginResult.Failure(AppError.Configuration(MISSING_KEY))
        return when (val result = api.postJson(EMAIL_LOGIN, body, RequestOptions.Sensitive)) {
            is DataResult.Failure -> EmailLoginResult.Failure(result.error)
            is DataResult.Success -> result.data.toEmailLoginResult()
        }
    }

    /** Verifies [code] for [email]. On success the caller stores [OtpVerificationResult.Verified.session]. */
    override suspend fun verifyEmailOtp(email: String, code: String): OtpVerificationResult {
        val address = email.trim()
        val body = encryptedBody {
            put("email", address)
            put("otp", code)
            put("deviceDetail", deviceInfo.deviceName)
        } ?: return OtpVerificationResult.Failure(AppError.Configuration(MISSING_KEY))
        return when (val result = api.postJson(VERIFY_EMAIL_OTP, body, RequestOptions.Sensitive)) {
            is DataResult.Failure -> OtpVerificationResult.Failure(result.error)
            is DataResult.Success -> result.data.toVerification(address, RegistrationRule.NO_LINKED_CONTACT)
        }
    }

    /** Signs a device out of the account ([all] = every device, using [key] of the first). */
    override suspend fun signOutDevice(key: DeviceSignOutKey, all: Boolean): SignOutDeviceResult {
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
    override suspend fun continueAfterDeviceSignOut(email: String): OtpVerificationResult {
        val address = email.trim()
        val fields = listOf(
            "is_email_verified" to "1",
            "email" to address,
            "deviceDetail" to deviceInfo.deviceName,
            "browsername" to "",
        )
        return when (val result = api.postForm(VERIFY_EMAIL_LOGIN, fields, RequestOptions.Sensitive)) {
            is DataResult.Failure -> OtpVerificationResult.Failure(result.error)
            is DataResult.Success -> result.data.toVerification(address, RegistrationRule.NO_LINKED_CONTACT)
        }
    }

    /** Starts a guest session for this device (`{"uuid": device id}`, not encrypted). */
    override suspend fun guestLogin(): GuestLoginResult {
        val deviceId = deviceInfo.deviceId?.takeIf { it.isNotBlank() }
            ?: return GuestLoginResult.Failure(AppError.Configuration("No device id for a guest session"))
        val body = buildJsonObject { put("uuid", deviceId) }
        return when (val result = api.postJson(GUEST_LOGIN, body, RequestOptions.Sensitive)) {
            is DataResult.Failure -> GuestLoginResult.Failure(result.error)
            is DataResult.Success -> result.data.toGuestResult()
        }
    }

    /** Ends the current session on the server (the `Authorization` header says which). */
    override suspend fun logout(): LogoutResult =
        when (val result = api.postJson(LOGOUT, JsonObject(emptyMap()), RequestOptions.Sensitive)) {
            is DataResult.Failure -> LogoutResult.Failure(result.error)
            is DataResult.Success -> result.data.toLogoutResult()
        }

    override suspend fun requestPhoneLogin(phone: PhoneNumber): PhoneLoginResult =
        when (val result = api.postJson(PHONE_LOGIN, phoneLoginBody(phone, verified = false), RequestOptions.Sensitive)) {
            is DataResult.Failure -> PhoneLoginResult.Failure(result.error)
            is DataResult.Success -> result.data.toPhoneLoginResult()
        }

    override suspend fun completePhoneLogin(phone: PhoneNumber): OtpVerificationResult =
        when (val result = api.postJson(PHONE_LOGIN, phoneLoginBody(phone, verified = true), RequestOptions.Sensitive)) {
            is DataResult.Failure -> OtpVerificationResult.Failure(result.error)
            is DataResult.Success -> result.data.toVerification(requestedEmail = null, RegistrationRule.NEW_ACCOUNT_STATUS)
        }

    /** `auth/login` with the UIKit app's `LoginRequestModel` keys; `profile_picture` only when known. */
    override suspend fun socialLogin(credential: SocialCredential): OtpVerificationResult {
        val body = buildJsonObject {
            put("login_type", credential.provider.loginType)
            put("phoneEmail", credential.email.orEmpty())
            put("password", "")
            put("type", "email")
            put("device_type", if (platform == ClientPlatform.IOS) "iOS" else platform.headerValue)
            put("device_token", deviceInfo.deviceId.orEmpty())
            put("social_user_id", credential.socialUserId)
            put("id_token", credential.token)
            put("first_name", credential.name.orEmpty())
            credential.pictureUrl?.takeIf { it.isNotBlank() }?.let { put("profile_picture", it) }
            put("deviceDetail", deviceInfo.deviceName)
            put("browsername", "")
        }
        return when (val result = api.postJson(SOCIAL_LOGIN, body, RequestOptions.Sensitive)) {
            is DataResult.Failure -> OtpVerificationResult.Failure(result.error)
            is DataResult.Success -> result.data.toSocialResult(credential.email)
        }
    }

    /**
     * UIKit `saveWithMobileOTP`: `auth/register` without `Authorization` (`defaultHeader`). The three
     * flags are 1 unless `plans/check_base_plan` reports a claimed base plan (UIKit `isBasePlan`). The
     * UIKit app also appends `?1` (the flag's value, with no name) to the path; it carries nothing, so
     * it is left out.
     */
    override suspend fun registerPhoneAccount(details: RegistrationDetails, pending: AuthSession?): RegistrationResult {
        val phone = details.phone ?: return RegistrationResult.Failure(AppError.Configuration("No verified phone number"))
        val flag = basePlanFlag(pending)
        val body = buildJsonObject {
            put("first_name", details.name.trim())
            put("email", details.email?.trim().orEmpty())
            put("phone", phone.nationalNumber)
            put("iso", (details.regionCode ?: DEFAULT_REGION).lowercase())
            put("country_code", phone.dialCode)
            // A number, as the UIKit app sends it here (unlike `phone-login`).
            put("is_phone_verified", 0)
            put("login_type", "normal")
            put("browsername", if (platform == ClientPlatform.IOS) "iOS" else platform.headerValue)
            put("deviceDetail", deviceInfo.deviceName)
            put("is_coins_credited", flag)
            put("push_notification_status", flag)
            put("email_notification_status", flag)
        }
        val options = RequestOptions.Sensitive.copy(sendAuthorization = false)
        return when (val result = api.postJson(REGISTER, body, options)) {
            is DataResult.Failure -> RegistrationResult.Failure(result.error)
            is DataResult.Success -> {
                val answer = result.data
                if (!answer.isRegistrationAccepted) return answer.refusal(RegistrationResult::Rejected, RegistrationResult::Failure)
                val session = answer.user()?.toSession(details.email?.trim(), guest = false)
                    ?: pending?.copy(firstName = details.name.trim(), email = details.email?.trim(), phone = phone.nationalNumber)
                    ?: return RegistrationResult.Failure(AppError.Parsing)
                RegistrationResult.Registered(session)
            }
        }
    }

    /**
     * UIKit `saveWithEmailOTP` / `SignupViewModel.updateProfileDetails`. The UIKit app always sends
     * `referal_code` (its text field is never nil, so an empty one goes as "") and `is_new_user`.
     */
    override suspend fun completeEmailAccount(details: RegistrationDetails, session: AuthSession): RegistrationResult {
        val phone = details.phone ?: return RegistrationResult.Failure(AppError.Configuration("No phone number"))
        val body = buildJsonObject {
            put("phone", phone.nationalNumber)
            put("first_name", details.name.trim())
            put("is_phone_verified", "1")
            put("country_code", phone.dialCode)
            put("country_iso_code", (details.regionCode ?: DEFAULT_REGION).lowercase())
            put("referal_code", details.referralCode?.trim().orEmpty())
            put("is_new_user", true)
        }
        return when (val result = api.postJson(UPDATE_PROFILE, body, RequestOptions.pendingSession(session.requestTokens))) {
            is DataResult.Failure -> RegistrationResult.Failure(result.error)
            is DataResult.Success -> {
                val answer = result.data
                if (!answer.isRegistrationAccepted) return answer.refusal(RegistrationResult::Rejected, RegistrationResult::Failure)
                RegistrationResult.Registered(session.copy(firstName = details.name.trim(), phone = phone.nationalNumber))
            }
        }
    }

    /**
     * UIKit `getCoinsPlansClaimsCheck`: 1 when no base plan was claimed (`response.count` 0) or the
     * check has no answer, else 0. Asked with the pending session's token when there is one.
     */
    private suspend fun basePlanFlag(pending: AuthSession?): Int {
        val answer = (api.get(BASE_PLAN_CHECK, options = RequestOptions.pendingSession(pending?.requestTokens)) as? DataResult.Success)?.data
            ?: return 1
        val count = answer.user()?.get("count").lenientInt()
        return if (answer.isRegistrationAccepted && count != null && count > 0) 0 else 1
    }

    /**
     * `auth/refresh-token` with the stored refresh token, without the rejected access token. A new
     * `response.access_token` is [TokenRefreshResult.Refreshed]; a refusal ends the session; no
     * usable answer (offline, 5xx) keeps it.
     */
    internal suspend fun refreshAccessToken(refreshToken: String): TokenRefreshResult {
        val body = buildJsonObject { put("refresh_token", refreshToken) }
        return when (val result = api.postJson(REFRESH_TOKEN, body, RequestOptions.TokenRefresh)) {
            is DataResult.Failure -> TokenRefreshResult.Failed(result.error)
            is DataResult.Success -> {
                val answer = result.data
                val token = answer.user()?.get("access_token").lenientString()?.takeIf { it.isNotBlank() }
                when {
                    answer.authStatus == STATUS_OK && !answer.reportsError && token != null -> TokenRefreshResult.Refreshed(token)
                    answer.authStatus >= SERVER_ERROR -> TokenRefreshResult.Failed(AppError.Server(answer.authStatus, answer.message))
                    else -> TokenRefreshResult.Rejected
                }
            }
        }
    }

    private fun phoneLoginBody(phone: PhoneNumber, verified: Boolean): JsonElement = buildJsonObject {
        put("phone", phone.nationalNumber)
        // A string, as the UIKit app sends it.
        put("is_phone_verified", if (verified) "1" else "0")
        put("deviceDetail", deviceInfo.deviceName)
        put("browsername", "")
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

    /** `phone-login` "0": the same status contract as `email-login` (200 known, 203 new, 202 device limit). */
    private fun VPlayedResponse.toPhoneLoginResult(): PhoneLoginResult {
        if (isHttpSuccess && envelope == null) return PhoneLoginResult.Failure(AppError.Parsing)
        val status = authStatus
        return when {
            status == STATUS_DEVICE_LIMIT -> PhoneLoginResult.DeviceLimitReached(message, user()?.toDeviceLimit())
            status == STATUS_NEW_USER -> PhoneLoginResult.Accepted(message, isNewUser = true)
            status == STATUS_OK && !reportsError -> PhoneLoginResult.Accepted(message, isNewUser = false)
            else -> refusal(PhoneLoginResult::Rejected, PhoneLoginResult::Failure)
        }
    }

    /**
     * `auth/login`: 202 is the device limit; 200–203 sign in (203 = new account, which must register
     * first when it comes without a session); the `error` flag is not read, as in the UIKit app.
     */
    private fun VPlayedResponse.toSocialResult(requestedEmail: String?): OtpVerificationResult {
        if (isHttpSuccess && envelope == null) return OtpVerificationResult.Failure(AppError.Parsing)
        val status = authStatus
        return when (status) {
            STATUS_DEVICE_LIMIT -> OtpVerificationResult.DeviceLimitReached(message, user()?.toDeviceLimit())
            in SOCIAL_SIGNED_IN -> {
                val user = user()
                val session = user?.toSession(requestedEmail, guest = false)
                when {
                    session != null -> OtpVerificationResult.Verified(
                        session = session,
                        isNewUser = status == STATUS_NEW_USER || user["isNewUsers"].lenientBool() == true,
                        hasMultipleProfiles = (user["profile_count"].lenientInt() ?: 0) > 1,
                    )
                    status == STATUS_NEW_USER -> OtpVerificationResult.RegistrationRequired(message)
                    else -> OtpVerificationResult.Failure(AppError.Parsing)
                }
            }
            else -> refusal(OtpVerificationResult::Rejected, OtpVerificationResult::Failure)
        }
    }

    /** When a verified sign-in must finish sign-up first (UIKit `OtpViewController`). */
    private enum class RegistrationRule {
        /** Email sign-in: the account has no phone and no Google / Apple / Facebook id (`handleMailOtpStatus200`). */
        NO_LINKED_CONTACT,

        /** Phone sign-in: `phone-login` "1" answered 203 (`handleRegisterOtpResponse`). */
        NEW_ACCOUNT_STATUS,
    }

    /** Shared by `verifyEmailOtp`, `verifyEmailLogin` and `phone-login` "1": same response contract. */
    private fun VPlayedResponse.toVerification(requestedEmail: String?, rule: RegistrationRule): OtpVerificationResult {
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
                        needsRegistration = when (rule) {
                            RegistrationRule.NO_LINKED_CONTACT -> LINKED_CONTACT_FIELDS.none { user[it].lenientString() != null }
                            RegistrationRule.NEW_ACCOUNT_STATUS -> status == STATUS_NEW_USER
                        },
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

    /** A 2xx answer that reports no error (sign-up calls; the UIKit app only checks that the call succeeded). */
    private val VPlayedResponse.isRegistrationAccepted: Boolean
        get() = authStatus in 200..299 && !reportsError

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
        internal const val PHONE_LOGIN = "users/api/v1/auth/phone-login"
        internal const val SOCIAL_LOGIN = "users/api/v1/auth/login"
        internal const val REFRESH_TOKEN = "users/api/v1/auth/refresh-token"
        internal const val REGISTER = "users/api/v1/auth/register"
        internal const val UPDATE_PROFILE = "users/api/v1/auth/update-profile"
        internal const val BASE_PLAN_CHECK = "mediadetails/api/v1/plans/check_base_plan"

        /** Any of these on an email account means it is set up (UIKit `hasNoAuthProvider`). */
        private val LINKED_CONTACT_FIELDS = listOf("phone", "apple_auth_id", "facebook_auth_id", "google_auth_id")

        /** The UIKit app's region when none is known. */
        private const val DEFAULT_REGION = "in"

        private const val STATUS_OK = 200
        private const val STATUS_DEVICE_LIMIT = 202
        private const val STATUS_NEW_USER = 203
        private val SESSION_ENDED = setOf(HTTP_UNAUTHORIZED, 403)
        private val LOGOUT_ACCEPTED = 200..203
        private val SOCIAL_SIGNED_IN = setOf(200, 201, 203)
        private const val SERVER_ERROR = 500
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
