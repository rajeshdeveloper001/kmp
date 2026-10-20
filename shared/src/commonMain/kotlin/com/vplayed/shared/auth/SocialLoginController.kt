package com.vplayed.shared.auth

import com.vplayed.shared.profile.SessionProfileChooser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SocialLoginState(
    /** The provider whose sign-in is running (SDK sheet or `auth/login`); the other buttons wait. */
    val inFlight: SocialProvider? = null,
    /** Signed in: the success step is showing until [SocialLoginEffect.Authenticated]. */
    val succeeded: Boolean = false,
    /** Informational line on the success step; none is set at present. */
    val notice: AuthMessage? = null,
)

sealed interface SocialLoginEffect {
    /** The user is signed in: the app stores [session] now (the success step is showing). */
    data class SignedIn(val session: AuthSession, val isNewUser: Boolean, val hasMultipleProfiles: Boolean) : SocialLoginEffect

    /** The success step has been shown for [AuthFlowConfig.successMillis]: leave sign-in. */
    data object Authenticated : SocialLoginEffect

    /** The account is at its device limit and the device step is enabled: open it (`OtpController.socialDevices`). */
    data class ShowDevices(val credential: SocialCredential, val limit: DeviceLimit) : SocialLoginEffect

    data class ShowMessage(val message: AuthMessage) : SocialLoginEffect
}

/**
 * Google and Apple sign-in (UIKit `SocialLoginDelegateHelpers` + `LoginViewController.socialLoginMethod`).
 * The app runs the provider's SDK and reports what happened: [onProviderStarted], then either
 * [onCredential] or [onProviderFinished]. This controller applies the UIKit app's checks and calls
 * `auth/login`. Same lifecycle rules as [EmailLoginController].
 */
class SocialLoginController(
    private val repository: SocialAuthRepository,
    private val scope: CoroutineScope,
    private val config: AuthFlowConfig,
    /** Picks the profile of the session before the app gets it (`SharedGraph.profileChooser`). */
    private val profileChooser: SessionProfileChooser = SessionProfileChooser.KeepDefault,
) {
    private val mutableState = MutableStateFlow(SocialLoginState())
    val state: StateFlow<SocialLoginState> = mutableState.asStateFlow()

    private val effectChannel = Channel<SocialLoginEffect>(Channel.BUFFERED)
    val effects: Flow<SocialLoginEffect> = effectChannel.receiveAsFlow()

    /** Whether a provider button may start now (nothing in flight, not signed in yet). */
    val canStart: Boolean get() = mutableState.value.let { it.inFlight == null && !it.succeeded }

    /** The provider's sheet is about to open. */
    fun onProviderStarted(provider: SocialProvider) {
        if (!canStart) return
        mutableState.update { it.copy(inFlight = provider) }
    }

    /** The provider's sheet closed without a credential: silent when the user cancelled, else "Login failed". */
    fun onProviderFinished(provider: SocialProvider, cancelled: Boolean) {
        if (mutableState.value.inFlight != provider) return
        mutableState.update { it.copy(inFlight = null) }
        if (!cancelled) send(SocialLoginEffect.ShowMessage(AuthMessage.Localized(AuthMessageKey.SOCIAL_LOGIN_FAILED)))
    }

    /** The provider returned [credential]: check it as the UIKit app does, then sign in with `auth/login`. */
    fun onCredential(credential: SocialCredential) {
        if (mutableState.value.succeeded) return
        val problem = when {
            credential.token.isBlank() -> AuthMessageKey.SOCIAL_LOGIN_FAILED
            credential.provider == SocialProvider.GOOGLE &&
                (credential.email.isNullOrBlank() || credential.socialUserId.isBlank()) -> AuthMessageKey.SOCIAL_LOGIN_FAILED
            credential.provider == SocialProvider.APPLE && credential.email.isNullOrBlank() -> AuthMessageKey.APPLE_EMAIL_REQUIRED
            else -> null
        }
        if (problem != null) {
            mutableState.update { it.copy(inFlight = null) }
            send(SocialLoginEffect.ShowMessage(AuthMessage.Localized(problem)))
            return
        }
        mutableState.update { it.copy(inFlight = credential.provider) }
        scope.launch {
            try {
                handle(repository.socialLogin(credential), credential)
            } finally {
                mutableState.update { it.copy(inFlight = null) }
            }
        }
    }

    /** Cancels the controller's work. Only for a controller that owns its scope. */
    fun close() = scope.cancel()

    /** The step is closed: reset social sign-in state unless a request is in flight. */
    fun onClosed() {
        if (mutableState.value.inFlight == null) clear()
    }

    /** Resets in-flight and succeeded state so another social sign-in can start. */
    fun clear() {
        mutableState.value = SocialLoginState()
    }

    private suspend fun handle(result: OtpVerificationResult, credential: SocialCredential) {
        when (result) {
            is OtpVerificationResult.Verified -> {
                // The first profile, without a picker (not built yet). Google / Apple accounts never
                // go through sign-up, as in the UIKit app.
                val session = profileChooser.choose(result.session)
                effectChannel.send(SocialLoginEffect.SignedIn(session, result.isNewUser, result.hasMultipleProfiles))
                mutableState.update { it.copy(succeeded = true, notice = null) }
                scope.launch {
                    delay(config.successMillis)
                    effectChannel.send(SocialLoginEffect.Authenticated)
                    mutableState.update { it.copy(succeeded = false) }
                }
            }
            is OtpVerificationResult.DeviceLimitReached -> effectChannel.send(
                if (result.limit != null && config.deviceSignOutEnabled) {
                    SocialLoginEffect.ShowDevices(credential, result.limit)
                } else {
                    SocialLoginEffect.ShowMessage(AuthMessage.serverOr(result.message, AuthMessageKey.DEVICE_LIMIT))
                },
            )
            is OtpVerificationResult.RegistrationRequired ->
                effectChannel.send(SocialLoginEffect.ShowMessage(AuthMessage.serverOr(result.message, AuthMessageKey.REGISTRATION_REQUIRED)))
            is OtpVerificationResult.Rejected ->
                effectChannel.send(SocialLoginEffect.ShowMessage(AuthMessage.serverOr(result.message, AuthMessageKey.SOCIAL_LOGIN_FAILED)))
            is OtpVerificationResult.Failure -> effectChannel.send(SocialLoginEffect.ShowMessage(AuthMessage.from(result.error)))
        }
    }

    private fun send(effect: SocialLoginEffect) {
        scope.launch { effectChannel.send(effect) }
    }
}
