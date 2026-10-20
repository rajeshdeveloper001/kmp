package com.vplayed.shared.auth

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Sends and checks SMS login codes. The backend does not send them: as in the UIKit app, the
 * platform's Firebase Auth does (AndroidJP `FirebasePhoneOtpSender`, VPlayed `FirebasePhoneOtpSender`).
 *
 * Callback-based so Swift can implement it. Each callback must be called exactly once, from any
 * thread; extra calls are ignored.
 */
interface PhoneOtpSender {
    /** Texts a code to [phoneNumber] (E.164, e.g. `+919876543210`). */
    fun sendCode(phoneNumber: String, onResult: (PhoneCodeSendResult) -> Unit)

    /** Checks [code] against the code sent for [verificationId]. */
    fun verifyCode(verificationId: String, code: String, onResult: (PhoneCodeCheckResult) -> Unit)
}

enum class PhoneCodeFailure { INVALID_NUMBER, TOO_MANY_REQUESTS, NETWORK, NOT_CONFIGURED, OTHER }

sealed interface PhoneCodeSendResult {
    /** The SMS is on its way; verify codes with [verificationId]. */
    data class CodeSent(val verificationId: String) : PhoneCodeSendResult {
        override fun toString(): String = "CodeSent(***)"
    }

    /** The platform verified the number without a code (Android instant verification). */
    data object VerifiedAutomatically : PhoneCodeSendResult

    data class Failed(val reason: PhoneCodeFailure) : PhoneCodeSendResult
}

sealed interface PhoneCodeCheckResult {
    data object Verified : PhoneCodeCheckResult

    /** Wrong or expired code (UIKit "Invalid OTP"). */
    data object InvalidCode : PhoneCodeCheckResult

    data class Failed(val reason: PhoneCodeFailure) : PhoneCodeCheckResult
}

internal suspend fun PhoneOtpSender.send(phoneNumber: String): PhoneCodeSendResult =
    suspendCancellableCoroutine { continuation ->
        sendCode(phoneNumber) { result -> if (continuation.isActive) runCatching { continuation.resume(result) } }
    }

internal suspend fun PhoneOtpSender.check(verificationId: String, code: String): PhoneCodeCheckResult =
    suspendCancellableCoroutine { continuation ->
        verifyCode(verificationId, code) { result -> if (continuation.isActive) runCatching { continuation.resume(result) } }
    }
