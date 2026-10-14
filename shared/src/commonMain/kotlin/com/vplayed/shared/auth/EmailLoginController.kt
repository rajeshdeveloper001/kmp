package com.vplayed.shared.auth

import com.vplayed.shared.validation.EmailValidationResult
import com.vplayed.shared.validation.EmailValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Behaviour switches for the sign-in flow. */
data class AuthFlowConfig(
    /**
     * Offer the device-limit step (list, sign out, continue). Its "continue" signs in without a code
     * (decision D5): AndroidJP enables it, VPlayed keeps it off until the backend confirms it is safe.
     * When off, a device-limit answer is shown as a message.
     */
    val deviceSignOutEnabled: Boolean,
    /** Seconds before a new code can be requested (previous apps: 60; the prototype uses 30). */
    val resendSeconds: Int = RESEND_SECONDS,
    /** How long the success step stays before [OtpEffect.Authenticated] (prototype: 3 s). */
    val successMillis: Long = SUCCESS_MILLIS,
) {
    companion object {
        /** Digits in a login code (prototype and previous apps). */
        const val CODE_LENGTH = 6
        const val RESEND_SECONDS = 60
        const val SUCCESS_MILLIS = 3_000L
    }
}

data class EmailLoginState(
    val email: String = "",
    /** EMPTY, INVALID_FORMAT or TOO_LONG while the field shows an error; null otherwise. */
    val emailError: EmailValidationResult? = null,
    /** A code request is in flight: input is locked and the button shows progress. */
    val isSubmitting: Boolean = false,
    /** A guest session request is in flight. */
    val isJoiningAsGuest: Boolean = false,
)

/** One-time outcomes of the email step, for the app to act on (navigate, show a message, store a session). */
sealed interface EmailLoginEffect {
    /** A code was sent to [email] (trimmed): open the code step. */
    data class CodeSent(val email: String, val isNewUser: Boolean) : EmailLoginEffect

    /** The account is at its device limit and the device step is enabled: open it with [limit]. */
    data class ShowDevices(val email: String, val limit: DeviceLimit) : EmailLoginEffect

    data class ShowMessage(val message: AuthMessage) : EmailLoginEffect

    /** A guest session started; the app stores [session]. */
    data class GuestSignedIn(val session: AuthSession) : EmailLoginEffect
}

/**
 * The email step of sign-in, shared by AndroidJP's `LoginViewModel` and VPlayed's login adapter:
 * validation (shared [EmailValidator], decision D16), one code request at a time, the outcome as an
 * [EmailLoginEffect], and guest login. State changes are synchronous; requests run in [scope].
 *
 * AndroidJP passes `viewModelScope`. VPlayed gets one from `SharedGraph.emailLoginController`,
 * which owns its scope, and calls [close] when the screen goes away.
 */
class EmailLoginController(
    private val repository: AuthRepository,
    private val scope: CoroutineScope,
    private val config: AuthFlowConfig,
) {
    private val mutableState = MutableStateFlow(EmailLoginState())
    val state: StateFlow<EmailLoginState> = mutableState.asStateFlow()

    private val effectChannel = Channel<EmailLoginEffect>(Channel.BUFFERED)
    val effects: Flow<EmailLoginEffect> = effectChannel.receiveAsFlow()

    fun onEmailChange(value: String) {
        mutableState.update { if (it.isSubmitting) it else it.copy(email = value, emailError = null) }
    }

    /** As in the prototype: a non-empty, malformed address is flagged when the field loses focus. */
    fun onEmailFocusLost() {
        val current = mutableState.value
        if (current.isSubmitting || current.email.isBlank()) return
        val result = EmailValidator.validate(current.email)
        if (result != EmailValidationResult.VALID) mutableState.update { it.copy(emailError = result) }
    }

    /** The email step is closed (close button): forget the typed email, unless a request is in flight. */
    fun onClosed() {
        if (!mutableState.value.isSubmitting) mutableState.value = EmailLoginState()
    }

    /** Signed in: the typed email has no further use. */
    fun clear() {
        mutableState.value = EmailLoginState()
    }

    fun onSubmit() {
        val current = mutableState.value
        if (current.isSubmitting || current.isJoiningAsGuest) return
        val validation = EmailValidator.validate(current.email)
        if (validation != EmailValidationResult.VALID) {
            mutableState.update { it.copy(emailError = validation) }
            return
        }
        mutableState.update { it.copy(isSubmitting = true, emailError = null) }
        scope.launch {
            try {
                effectChannel.send(repository.requestEmailLogin(current.email).toEffect(current.email.trim()))
            } finally {
                mutableState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun joinAsGuest() {
        val current = mutableState.value
        if (current.isSubmitting || current.isJoiningAsGuest) return
        mutableState.update { it.copy(isJoiningAsGuest = true) }
        scope.launch {
            try {
                val effect = when (val result = repository.guestLogin()) {
                    is GuestLoginResult.SignedIn -> EmailLoginEffect.GuestSignedIn(result.session)
                    is GuestLoginResult.Rejected ->
                        EmailLoginEffect.ShowMessage(AuthMessage.serverOr(result.message, AuthMessageKey.GUEST_LOGIN_FAILED))
                    is GuestLoginResult.Failure -> EmailLoginEffect.ShowMessage(AuthMessage.from(result.error))
                }
                effectChannel.send(effect)
            } finally {
                mutableState.update { it.copy(isJoiningAsGuest = false) }
            }
        }
    }

    /** Cancels the controller's work. Only for a controller that owns its scope (see the class comment). */
    fun close() = scope.cancel()

    private fun EmailLoginResult.toEffect(email: String): EmailLoginEffect = when (this) {
        is EmailLoginResult.CodeSent -> EmailLoginEffect.CodeSent(email, isNewUser)
        is EmailLoginResult.DeviceLimitReached ->
            if (limit != null && config.deviceSignOutEnabled) {
                EmailLoginEffect.ShowDevices(email, limit)
            } else {
                EmailLoginEffect.ShowMessage(AuthMessage.serverOr(message, AuthMessageKey.DEVICE_LIMIT))
            }
        is EmailLoginResult.Rejected -> EmailLoginEffect.ShowMessage(AuthMessage.serverOr(message, AuthMessageKey.LOGIN_FAILED))
        is EmailLoginResult.Failure -> EmailLoginEffect.ShowMessage(AuthMessage.from(error))
    }
}
