package com.vplayed.shared.auth

import com.vplayed.shared.core.error.AppError
import com.vplayed.shared.phone.PhoneNumber

/** How the code step's code reaches the user, if there is one. */
enum class OtpChannelKind { EMAIL, PHONE, SOCIAL }

/** What asking for a (new) code led to. */
internal sealed interface CodeRequestOutcome {
    /** A code is on its way; [info] is a neutral line to show (null: the restarted countdown is the feedback). */
    data class Sent(val info: AuthMessage?) : CodeRequestOutcome

    data class DeviceLimitReached(val message: String?, val limit: DeviceLimit?) : CodeRequestOutcome

    data class Refused(val message: AuthMessage) : CodeRequestOutcome

    /** The platform verified the phone number without a code (Android): finish the login. */
    data object AutoVerified : CodeRequestOutcome
}

/** What differs between email, phone and social sign-in on the code, device and success steps. */
internal interface OtpChannel {
    val kind: OtpChannelKind

    /** Where the code goes, as the code step shows it. */
    val destination: String

    /** False for social sign-in: only the device step is used. */
    val hasCodeStep: Boolean

    /** True when the controller sends the first code itself (phone); email codes are sent by the email step. */
    val sendsCodeOnStart: Boolean

    /** Sends a new code (Resend, or the first SMS). */
    suspend fun requestCode(): CodeRequestOutcome

    /** Asks again after devices were signed out, when the device limit came from the first request. */
    suspend fun retryLoginRequest(): CodeRequestOutcome

    suspend fun verify(code: String): OtpVerificationResult

    /** Device step: signs one device ([all] = every device) out of the account (`auth/logout-device`). */
    suspend fun signOutDevice(key: DeviceSignOutKey, all: Boolean): SignOutDeviceResult

    /** Signs in after devices were signed out (or after automatic verification), without a code. */
    suspend fun completeWithoutCode(): OtpVerificationResult

    /** The sign-up [result] asks for, if any (UIKit `RegisterViewController`); null signs in or shows the result. */
    fun registrationFor(result: OtpVerificationResult): PendingRegistration? = null
}

/** Email sign-in: codes come from `auth/email-login`, checked by `auth/verifyEmailOtp`. */
internal class EmailOtpChannel(
    private val email: String,
    private val repository: AuthRepository,
) : OtpChannel {
    override val kind = OtpChannelKind.EMAIL
    override val destination = email
    override val hasCodeStep = true
    override val sendsCodeOnStart = false

    override suspend fun requestCode(): CodeRequestOutcome = when (val result = repository.requestEmailLogin(email)) {
        is EmailLoginResult.CodeSent -> CodeRequestOutcome.Sent(info = null)
        is EmailLoginResult.DeviceLimitReached -> CodeRequestOutcome.DeviceLimitReached(result.message, result.limit)
        is EmailLoginResult.Rejected -> CodeRequestOutcome.Refused(AuthMessage.serverOr(result.message, AuthMessageKey.LOGIN_FAILED))
        is EmailLoginResult.Failure -> CodeRequestOutcome.Refused(AuthMessage.from(result.error))
    }

    override suspend fun retryLoginRequest(): CodeRequestOutcome = requestCode()

    override suspend fun verify(code: String): OtpVerificationResult = repository.verifyEmailOtp(email, code)

    override suspend fun signOutDevice(key: DeviceSignOutKey, all: Boolean) = repository.signOutDevice(key, all)

    /** Decision D5: `verifyEmailLogin`, without a code. */
    override suspend fun completeWithoutCode(): OtpVerificationResult = repository.continueAfterDeviceSignOut(email)

    /** A verified account with no phone and no linked social id: name and mobile number first. */
    override fun registrationFor(result: OtpVerificationResult): PendingRegistration? =
        if (result is OtpVerificationResult.Verified && result.needsRegistration) {
            PendingRegistration(RegistrationKind.EMAIL_ACCOUNT, result.session, email = email.trim(), phone = null)
        } else {
            null
        }
}

/**
 * Phone sign-in (UIKit `OtpViewController` for `.mobile`): the platform texts and checks the code;
 * then `auth/phone-login` with "1" signs in. After devices were signed out it asks `phone-login`
 * again — not `verifyEmailOtp`, which the UIKit app called by mistake.
 */
internal class PhoneOtpChannel(
    private val phone: PhoneNumber,
    private val repository: PhoneAuthRepository,
    private val sender: PhoneOtpSender,
    /** For `logout-device` on the device step. */
    private val accounts: AuthRepository,
) : OtpChannel {
    override val kind = OtpChannelKind.PHONE
    override val destination = phone.display
    override val hasCodeStep = true
    override val sendsCodeOnStart = true

    private var verificationId: String? = null

    override suspend fun requestCode(): CodeRequestOutcome = when (val result = sender.send(phone.e164)) {
        is PhoneCodeSendResult.CodeSent -> {
            verificationId = result.verificationId
            CodeRequestOutcome.Sent(AuthMessage.Localized(AuthMessageKey.PHONE_CODE_SENT))
        }
        PhoneCodeSendResult.VerifiedAutomatically -> CodeRequestOutcome.AutoVerified
        is PhoneCodeSendResult.Failed -> CodeRequestOutcome.Refused(AuthMessage.Localized(result.reason.sendMessageKey()))
    }

    override suspend fun retryLoginRequest(): CodeRequestOutcome = when (val result = repository.requestPhoneLogin(phone)) {
        is PhoneLoginResult.Accepted -> requestCode()
        is PhoneLoginResult.DeviceLimitReached -> CodeRequestOutcome.DeviceLimitReached(result.message, result.limit)
        is PhoneLoginResult.Rejected -> CodeRequestOutcome.Refused(AuthMessage.serverOr(result.message, AuthMessageKey.LOGIN_FAILED))
        is PhoneLoginResult.Failure -> CodeRequestOutcome.Refused(AuthMessage.from(result.error))
    }

    override suspend fun verify(code: String): OtpVerificationResult {
        val id = verificationId ?: return OtpVerificationResult.Rejected(null)
        return when (val check = sender.check(id, code)) {
            PhoneCodeCheckResult.Verified -> repository.completePhoneLogin(phone)
            // Shown as "invalid code", like the UIKit app's "Invalid OTP".
            PhoneCodeCheckResult.InvalidCode -> OtpVerificationResult.Rejected(null)
            is PhoneCodeCheckResult.Failed -> OtpVerificationResult.Failure(
                if (check.reason == PhoneCodeFailure.NETWORK) AppError.Network else AppError.Unknown(),
            )
        }
    }

    override suspend fun signOutDevice(key: DeviceSignOutKey, all: Boolean) = accounts.signOutDevice(key, all)

    override suspend fun completeWithoutCode(): OtpVerificationResult = repository.completePhoneLogin(phone)

    /** `phone-login` "1" answered 203, with or without a session: name and email first. */
    override fun registrationFor(result: OtpVerificationResult): PendingRegistration? = when {
        result is OtpVerificationResult.Verified && result.needsRegistration ->
            PendingRegistration(RegistrationKind.PHONE_ACCOUNT, result.session, email = null, phone = phone)
        result is OtpVerificationResult.RegistrationRequired ->
            PendingRegistration(RegistrationKind.PHONE_ACCOUNT, session = null, email = null, phone = phone)
        else -> null
    }

    private fun PhoneCodeFailure.sendMessageKey(): AuthMessageKey = when (this) {
        PhoneCodeFailure.INVALID_NUMBER -> AuthMessageKey.PHONE_INVALID
        PhoneCodeFailure.NETWORK -> AuthMessageKey.ERROR_NETWORK
        PhoneCodeFailure.NOT_CONFIGURED -> AuthMessageKey.PHONE_LOGIN_UNAVAILABLE
        PhoneCodeFailure.TOO_MANY_REQUESTS, PhoneCodeFailure.OTHER -> AuthMessageKey.PHONE_CODE_SEND_FAILED
    }
}

/** Google / Apple sign-in at its device limit: only the device step; Continue signs in with the same credential. */
internal class SocialOtpChannel(
    private val credential: SocialCredential,
    private val repository: SocialAuthRepository,
    /** For `logout-device` on the device step. */
    private val accounts: AuthRepository,
) : OtpChannel {
    override val kind = OtpChannelKind.SOCIAL
    override val destination = credential.email.orEmpty()
    override val hasCodeStep = false
    override val sendsCodeOnStart = false

    override suspend fun requestCode(): CodeRequestOutcome = CodeRequestOutcome.Sent(info = null)

    override suspend fun retryLoginRequest(): CodeRequestOutcome = CodeRequestOutcome.Sent(info = null)

    override suspend fun verify(code: String): OtpVerificationResult = completeWithoutCode()

    override suspend fun signOutDevice(key: DeviceSignOutKey, all: Boolean) = accounts.signOutDevice(key, all)

    override suspend fun completeWithoutCode(): OtpVerificationResult = repository.socialLogin(credential)
}
