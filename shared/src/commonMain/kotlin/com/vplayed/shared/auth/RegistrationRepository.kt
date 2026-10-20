package com.vplayed.shared.auth

/**
 * Sign-up of a new account after its first sign-in (UIKit `SignupViewModel`, used by
 * `RegisterViewController`). Both calls return the session to store; neither stores it.
 */
interface RegistrationRepository {
    /**
     * [RegistrationKind.PHONE_ACCOUNT]: `auth/register` with the verified number, the name and the
     * email (UIKit `saveWithMobileOTP`). [pending] is the session `phone-login` issued, if any.
     */
    suspend fun registerPhoneAccount(details: RegistrationDetails, pending: AuthSession?): RegistrationResult

    /**
     * [RegistrationKind.EMAIL_ACCOUNT]: `auth/update-profile` with the name and mobile number,
     * authorised by [session] (UIKit `saveWithEmailOTP`); returns [session] with them.
     */
    suspend fun completeEmailAccount(details: RegistrationDetails, session: AuthSession): RegistrationResult
}
