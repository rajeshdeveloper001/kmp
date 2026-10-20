package com.vplayed.shared.auth

import com.vplayed.shared.phone.PhoneNumber

/**
 * Phone sign-in against `auth/phone-login` (the UIKit app's `LoginRepository.loginWithOTPRequest`).
 * The code itself is sent and checked by [PhoneOtpSender]; the backend is told before ("0") and
 * after ("1") that check. Only the national number is sent, as in the UIKit app.
 */
interface PhoneAuthRepository {
    /** `is_phone_verified` "0": is this a known (200) or new (203) account, or at its device limit (202)? */
    suspend fun requestPhoneLogin(phone: PhoneNumber): PhoneLoginResult

    /** `is_phone_verified` "1", once the code was verified: signs in like `verifyEmailOtp`. */
    suspend fun completePhoneLogin(phone: PhoneNumber): OtpVerificationResult
}
