package com.vplayed.shared.auth

import com.vplayed.shared.phone.CountryDialCode
import com.vplayed.shared.phone.CountryDialCodes
import com.vplayed.shared.phone.PhoneFormatting
import com.vplayed.shared.phone.PhoneNumber
import com.vplayed.shared.profile.SessionProfileChooser
import com.vplayed.shared.validation.EmailValidationResult
import com.vplayed.shared.validation.EmailValidator
import com.vplayed.shared.validation.NameValidationResult
import com.vplayed.shared.validation.NameValidator
import com.vplayed.shared.validation.PhoneValidationResult
import com.vplayed.shared.validation.PhoneValidator
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

enum class RegistrationStep { Form, Success }

data class RegistrationState(
    val kind: RegistrationKind,
    val step: RegistrationStep = RegistrationStep.Form,
    /** Letters, digits and spaces only ([NameValidator.sanitize]). */
    val name: String = "",
    val nameError: NameValidationResult? = null,
    /** Typed for a phone account; the email signed in with (read-only) for an email account. */
    val email: String = "",
    val emailError: EmailValidationResult? = null,
    /** The mobile number's country: chosen for an email account, the verified number's for a phone account. */
    val country: CountryDialCode,
    /** National number, digits only: typed for an email account, the verified one for a phone account. */
    val number: String = "",
    val numberError: PhoneValidationResult? = null,
    /** "Have a referral code?" was tapped: the referral field shows (email accounts only). */
    val isReferralVisible: Boolean = false,
    val referralCode: String = "",
    val isSubmitting: Boolean = false,
    /** Why the last submit failed (server refusal, no connection, …); cleared by the next edit or submit. */
    val error: AuthMessage? = null,
) {
    /** An email account types its mobile number; a phone account its email. The other is shown read-only. */
    val isPhoneEditable: Boolean get() = kind == RegistrationKind.EMAIL_ACCOUNT
    val isEmailEditable: Boolean get() = kind == RegistrationKind.PHONE_ACCOUNT

    /** Only `auth/update-profile` (email accounts) takes a referral code. */
    val offersReferral: Boolean get() = kind == RegistrationKind.EMAIL_ACCOUNT

    /** The number as the field shows it (`98765 43210`). */
    val formattedNumber: String get() = PhoneFormatting.group(number)
}

sealed interface RegistrationEffect {
    /** Sign-up is done: the app stores [session] now (the success step is showing). */
    data class SignedIn(val session: AuthSession) : RegistrationEffect

    /** The success step has been shown for [AuthFlowConfig.successMillis]: leave sign-in. */
    data object Authenticated : RegistrationEffect

    /**
     * The form was left before sign-up finished: close sign-in and show [message] ("Your signup was
     * unsuccessful."). Nothing was stored, so the user stays as before (guest or signed out).
     */
    data class Cancelled(val message: AuthMessage) : RegistrationEffect
}

/**
 * The sign-up form a new account fills in after its first sign-in (UIKit `RegisterViewController`;
 * prototype "Complete your profile"). An email account adds its name and mobile number
 * (`auth/update-profile`, optional referral code); a phone account adds its name and email
 * (`auth/register`). The session is stored only once that succeeds, with the account's first
 * profile ([SessionProfileChooser]). Same lifecycle rules as [EmailLoginController].
 */
class RegistrationController(
    private val registration: PendingRegistration,
    private val repository: RegistrationRepository,
    private val scope: CoroutineScope,
    private val config: AuthFlowConfig,
    private val profileChooser: SessionProfileChooser = SessionProfileChooser.KeepDefault,
) {
    private val mutableState = MutableStateFlow(initialState(registration))
    val state: StateFlow<RegistrationState> = mutableState.asStateFlow()

    private val effectChannel = Channel<RegistrationEffect>(Channel.BUFFERED)
    val effects: Flow<RegistrationEffect> = effectChannel.receiveAsFlow()

    fun onNameChange(value: String) = edit { it.copy(name = NameValidator.sanitize(value), nameError = null) }

    fun onEmailChange(value: String) = edit {
        if (it.isEmailEditable) it.copy(email = value.trim().take(EmailValidator.MAX_LENGTH), emailError = null) else it
    }

    fun onCountryChange(country: CountryDialCode) = edit {
        if (it.isPhoneEditable) it.copy(country = country, numberError = null) else it
    }

    fun onNumberChange(value: String) = edit {
        if (it.isPhoneEditable) it.copy(number = PhoneValidator.sanitize(value), numberError = null) else it
    }

    /** "Have a referral code?" */
    fun onShowReferral() = edit { if (it.offersReferral) it.copy(isReferralVisible = true) else it }

    fun onReferralChange(value: String) = edit { it.copy(referralCode = value.trim().take(MAX_REFERRAL_LENGTH)) }

    fun onSubmit() {
        val current = mutableState.value
        if (current.isSubmitting || current.step != RegistrationStep.Form) return
        val nameCheck = NameValidator.validate(current.name)
        val emailCheck = if (current.isEmailEditable) EmailValidator.validate(current.email) else EmailValidationResult.VALID
        val numberCheck =
            if (current.isPhoneEditable) PhoneValidator.validate(current.country.dialCode, current.number) else PhoneValidationResult.VALID
        if (nameCheck != NameValidationResult.VALID || emailCheck != EmailValidationResult.VALID || numberCheck != PhoneValidationResult.VALID) {
            mutableState.update {
                it.copy(
                    nameError = nameCheck.takeUnless { r -> r == NameValidationResult.VALID },
                    emailError = emailCheck.takeUnless { r -> r == EmailValidationResult.VALID },
                    numberError = numberCheck.takeUnless { r -> r == PhoneValidationResult.VALID },
                )
            }
            return
        }
        val details = RegistrationDetails(
            name = current.name.trim(),
            email = current.email.trim().ifEmpty { null },
            // A phone account sends the number it verified, exactly.
            phone = registration.phone.takeUnless { current.isPhoneEditable } ?: PhoneNumber(current.country.dialCode, current.number),
            regionCode = current.country.regionCode,
            referralCode = current.referralCode.takeIf { current.isReferralVisible },
        )
        mutableState.update { it.copy(isSubmitting = true, error = null) }
        scope.launch {
            try {
                handle(register(details))
            } finally {
                mutableState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    /** Back / close: leaves sign-up unless it is being sent or done. */
    fun onBack() {
        val current = mutableState.value
        if (current.isSubmitting || current.step == RegistrationStep.Success) return
        scope.launch {
            effectChannel.send(RegistrationEffect.Cancelled(AuthMessage.Localized(AuthMessageKey.REGISTRATION_CANCELLED)))
        }
    }

    /** Cancels the controller's work. Only for a controller that owns its scope (`SharedGraph.registrationController`). */
    fun close() = scope.cancel()

    private suspend fun register(details: RegistrationDetails): RegistrationResult = when (registration.kind) {
        RegistrationKind.PHONE_ACCOUNT -> repository.registerPhoneAccount(details, registration.session)
        RegistrationKind.EMAIL_ACCOUNT -> {
            val session = registration.session
            if (session == null) {
                RegistrationResult.Rejected(null)
            } else {
                repository.completeEmailAccount(details, session)
            }
        }
    }

    private suspend fun handle(result: RegistrationResult) {
        when (result) {
            is RegistrationResult.Registered -> {
                // The first profile, without a picker (not built yet), as after any sign-in.
                val session = profileChooser.choose(result.session)
                effectChannel.send(RegistrationEffect.SignedIn(session))
                mutableState.update { it.copy(step = RegistrationStep.Success, error = null) }
                scope.launch {
                    delay(config.successMillis)
                    effectChannel.send(RegistrationEffect.Authenticated)
                }
            }
            is RegistrationResult.Rejected ->
                mutableState.update { it.copy(error = AuthMessage.serverOr(result.message, AuthMessageKey.REGISTRATION_FAILED)) }
            is RegistrationResult.Failure -> mutableState.update { it.copy(error = AuthMessage.from(result.error)) }
        }
    }

    /** Applies [change] while the form is editable; any edit clears the last submit error. */
    private fun edit(change: (RegistrationState) -> RegistrationState) {
        mutableState.update { if (it.isSubmitting || it.step != RegistrationStep.Form) it else change(it).copy(error = null) }
    }

    private companion object {
        /** Referral codes are short; the UIKit field has no limit, so this only stops runaway input. */
        const val MAX_REFERRAL_LENGTH = 32

        fun initialState(registration: PendingRegistration): RegistrationState {
            val phone = registration.phone
            return RegistrationState(
                kind = registration.kind,
                name = NameValidator.sanitize(registration.session?.firstName.orEmpty()),
                email = registration.email ?: registration.session?.email.orEmpty(),
                // The UIKit form starts at +91 (`setCountryByPhoneCode(countryCode ?? "91")`).
                country = phone?.let { p -> CountryDialCodes.all.firstOrNull { it.dialCode == p.dialCode } } ?: CountryDialCodes.DEFAULT,
                number = phone?.nationalNumber.orEmpty(),
            )
        }
    }
}
