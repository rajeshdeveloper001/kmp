package com.vplayed.shared.auth

/** Google / Apple sign-in against `auth/login` (the UIKit app's `LoginRepository.loginWithPasswordRequest`). */
interface SocialAuthRepository {
    /** Signs in with what the platform SDK returned: 200–203 with a session, or 202 (device limit). */
    suspend fun socialLogin(credential: SocialCredential): OtpVerificationResult
}
