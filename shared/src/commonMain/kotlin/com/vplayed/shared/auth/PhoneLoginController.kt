package com.vplayed.shared.auth

import com.vplayed.shared.phone.CountryDialCode
import com.vplayed.shared.phone.PhoneFormatting
import com.vplayed.shared.phone.PhoneNumber
import com.vplayed.shared.validation.PhoneValidationResult
import com.vplayed.shared.validation.PhoneValidator
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

data class PhoneLoginState(
    val country: CountryDialCode,
    /** The national number, digits only ([PhoneValidator.sanitize]). */
    val number: String = "",
    /** Set while the field shows an error; null otherwise. */
    val numberError: PhoneValidationResult? = null,
    /** A `phone-login` request is in flight: input is locked and the button shows progress. */
    val isSubmitting: Boolean = false,
) {
    /** The number as the field shows it (`98765 43210`). */
    val formattedNumber: String get() = PhoneFormatting.group(number)
}

/** One-time outcomes of the phone step. */
sealed interface PhoneLoginEffect {
    /** The backend knows the number (or will create the account): open the code step, which texts the code. */
    data class CodeRequested(val phone: PhoneNumber, val isNewUser: Boolean) : PhoneLoginEffect

    /** The account is at its device limit and the device step is enabled. */
    data class ShowDevices(val phone: PhoneNumber, val limit: DeviceLimit) : PhoneLoginEffect

    data class ShowMessage(val message: AuthMessage) : PhoneLoginEffect
}

/**
 * The mobile-number step of sign-in, used when common settings choose phone verification
 * (`otp_verfied` = phone / mobile; UIKit `LoginViewController` in `.mobile` state). Validates with
 * [PhoneValidator], then asks `auth/phone-login` ("0") before the code step texts the code.
 * Same lifecycle rules as [EmailLoginController].
 */
class PhoneLoginController(
    private val repository: PhoneAuthRepository,
    private val scope: CoroutineScope,
    private val config: AuthFlowConfig,
    initialCountry: CountryDialCode,
) {
    private val mutableState = MutableStateFlow(PhoneLoginState(country = initialCountry))
    val state: StateFlow<PhoneLoginState> = mutableState.asStateFlow()

    private val effectChannel = Channel<PhoneLoginEffect>(Channel.BUFFERED)
    val effects: Flow<PhoneLoginEffect> = effectChannel.receiveAsFlow()

    fun onCountryChange(country: CountryDialCode) {
        mutableState.update { if (it.isSubmitting) it else it.copy(country = country, numberError = null) }
    }

    fun onNumberChange(value: String) {
        mutableState.update { if (it.isSubmitting) it else it.copy(number = PhoneValidator.sanitize(value), numberError = null) }
    }

    /** As the prototype: a non-empty, invalid number is flagged when the field loses focus. */
    fun onNumberFocusLost() {
        val current = mutableState.value
        if (current.isSubmitting || current.number.isEmpty()) return
        val result = PhoneValidator.validate(current.country.dialCode, current.number)
        if (result != PhoneValidationResult.VALID) mutableState.update { it.copy(numberError = result) }
    }

    /** The step is closed: forget the typed number, unless a request is in flight. */
    fun onClosed() {
        if (!mutableState.value.isSubmitting) clear()
    }

    /** Signed in: the typed number has no further use. */
    fun clear() {
        mutableState.update { PhoneLoginState(country = it.country) }
    }

    fun onSubmit() {
        val current = mutableState.value
        if (current.isSubmitting) return
        val validation = PhoneValidator.validate(current.country.dialCode, current.number)
        if (validation != PhoneValidationResult.VALID) {
            mutableState.update { it.copy(numberError = validation) }
            return
        }
        val phone = PhoneNumber(current.country.dialCode, current.number)
        mutableState.update { it.copy(isSubmitting = true, numberError = null) }
        scope.launch {
            try {
                effectChannel.send(repository.requestPhoneLogin(phone).toEffect(phone))
            } finally {
                mutableState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    /** Cancels the controller's work. Only for a controller that owns its scope. */
    fun close() = scope.cancel()

    private fun PhoneLoginResult.toEffect(phone: PhoneNumber): PhoneLoginEffect = when (this) {
        is PhoneLoginResult.Accepted -> PhoneLoginEffect.CodeRequested(phone, isNewUser)
        is PhoneLoginResult.DeviceLimitReached ->
            if (limit != null && config.deviceSignOutEnabled) {
                PhoneLoginEffect.ShowDevices(phone, limit)
            } else {
                PhoneLoginEffect.ShowMessage(AuthMessage.serverOr(message, AuthMessageKey.DEVICE_LIMIT))
            }
        is PhoneLoginResult.Rejected -> PhoneLoginEffect.ShowMessage(AuthMessage.serverOr(message, AuthMessageKey.LOGIN_FAILED))
        is PhoneLoginResult.Failure -> PhoneLoginEffect.ShowMessage(AuthMessage.from(error))
    }
}
