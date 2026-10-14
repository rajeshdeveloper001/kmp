package com.vplayed.shared.auth

import kotlinx.coroutines.CompletableDeferred

internal val testSession = AuthSession(
    accessToken = "access-1",
    refreshToken = "refresh-1",
    userId = "u-1",
    uniqueUserId = 42,
    email = "user@example.com",
    phone = null,
    firstName = "Sam",
    profileId = "p-1",
    isGuest = false,
)

internal fun verified(isNewUser: Boolean = false, hasMultipleProfiles: Boolean = false) =
    OtpVerificationResult.Verified(testSession, isNewUser, hasMultipleProfiles)

/** Scriptable [AuthRepository]: records each call and answers with the configured result, optionally held. */
internal class FakeAuthRepository : AuthRepository {
    var result: EmailLoginResult = EmailLoginResult.CodeSent(message = null, isNewUser = false)
    var verifyResult: OtpVerificationResult = verified()
    var continueResult: OtpVerificationResult = verified()
    var signOutResult: SignOutDeviceResult = SignOutDeviceResult.Success
    var guestResult: GuestLoginResult = GuestLoginResult.SignedIn(testSession.copy(isGuest = true))

    /** Holds the matching call until completed. */
    var hold: CompletableDeferred<Unit>? = null
    var verifyHold: CompletableDeferred<Unit>? = null
    var signOutHold: CompletableDeferred<Unit>? = null

    val requestedEmails = mutableListOf<String>()
    val verifications = mutableListOf<Pair<String, String>>()
    val signOuts = mutableListOf<Pair<DeviceSignOutKey, Boolean>>()
    val continuedEmails = mutableListOf<String>()
    var guestLogins = 0

    override suspend fun requestEmailLogin(email: String): EmailLoginResult {
        requestedEmails += email
        hold?.await()
        return result
    }

    override suspend fun verifyEmailOtp(email: String, code: String): OtpVerificationResult {
        verifications += email to code
        verifyHold?.await()
        return verifyResult
    }

    override suspend fun signOutDevice(key: DeviceSignOutKey, all: Boolean): SignOutDeviceResult {
        signOuts += key to all
        signOutHold?.await()
        return signOutResult
    }

    override suspend fun continueAfterDeviceSignOut(email: String): OtpVerificationResult {
        continuedEmails += email
        return continueResult
    }

    override suspend fun guestLogin(): GuestLoginResult {
        guestLogins++
        hold?.await()
        return guestResult
    }

    override suspend fun logout(): LogoutResult = LogoutResult.LoggedOut
}
