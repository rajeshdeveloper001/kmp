package com.vplayed.shared.l10n

import com.vplayed.shared.auth.AuthMessageKey
import com.vplayed.shared.core.error.AppError
import com.vplayed.shared.home.ShowType
import com.vplayed.shared.validation.EmailValidationResult
import com.vplayed.shared.validation.NameValidationResult
import com.vplayed.shared.validation.PhoneValidationResult
import org.jetbrains.compose.resources.StringResource

/*
 * The catalog string for each message key the shared layer returns, so both apps show the same text
 * for the same outcome. Some messages name the brand: format every one of them with the brand name as
 * the only argument (messages without a placeholder ignore it).
 */

/** The text for a sign-in message key. */
val AuthMessageKey.resource: StringResource
    get() = when (this) {
        AuthMessageKey.DEVICE_LIMIT -> Res.string.auth_device_limit
        AuthMessageKey.LOGIN_FAILED, AuthMessageKey.GUEST_LOGIN_FAILED -> Res.string.auth_login_failed
        AuthMessageKey.OTP_INVALID -> Res.string.auth_otp_invalid
        AuthMessageKey.REGISTRATION_REQUIRED -> Res.string.auth_registration_required
        AuthMessageKey.REGISTRATION_FAILED -> Res.string.auth_registration_failed
        AuthMessageKey.REGISTRATION_CANCELLED -> Res.string.auth_registration_cancelled
        AuthMessageKey.DEVICE_SIGN_OUT_FAILED -> Res.string.auth_device_sign_out_failed
        AuthMessageKey.PROFILE_SETUP_PENDING -> Res.string.auth_profile_setup_pending
        AuthMessageKey.MULTI_PROFILE_PENDING -> Res.string.auth_multi_profile_pending
        AuthMessageKey.PHONE_CODE_SENT -> Res.string.auth_phone_code_sent
        AuthMessageKey.PHONE_CODE_SEND_FAILED -> Res.string.auth_phone_code_send_failed
        AuthMessageKey.PHONE_LOGIN_UNAVAILABLE -> Res.string.auth_phone_login_unavailable
        AuthMessageKey.PHONE_INVALID -> Res.string.validation_phone_invalid
        AuthMessageKey.SOCIAL_LOGIN_FAILED -> Res.string.auth_social_login_failed
        AuthMessageKey.APPLE_EMAIL_REQUIRED -> Res.string.auth_apple_email_required
        AuthMessageKey.ERROR_NETWORK -> Res.string.error_unreachable
        AuthMessageKey.ERROR_TIMEOUT -> Res.string.error_timeout
        AuthMessageKey.ERROR_UNAUTHORIZED -> Res.string.error_session_expired
        AuthMessageKey.ERROR_SERVER, AuthMessageKey.ERROR_PARSING, AuthMessageKey.ERROR_UNKNOWN -> Res.string.error_generic
    }

/**
 * The text for a shared-layer failure. A server refusal that explains itself is shown in the
 * server's words instead ([serverMessage]).
 */
val AppError.messageResource: StringResource
    get() = when (this) {
        AppError.Network -> Res.string.error_unreachable
        AppError.Timeout -> Res.string.error_timeout
        AppError.Unauthorized -> Res.string.error_session_expired
        is AppError.Server, AppError.Parsing, is AppError.Configuration, is AppError.Unknown -> Res.string.error_generic
    }

/** The server's own explanation of a refusal, when it gave one. */
val AppError.serverMessage: String?
    get() = (this as? AppError.Server)?.message?.trim()?.takeIf { it.isNotEmpty() }

/** The sign-in screen's email error; the field is capped at the maximum length, so every error is about the address. */
val EmailValidationResult.signInMessage: StringResource?
    get() = if (this == EmailValidationResult.VALID) null else Res.string.validation_email_invalid

/** The registration form's email error. */
val EmailValidationResult.registrationMessage: StringResource?
    get() = when (this) {
        EmailValidationResult.VALID -> null
        EmailValidationResult.EMPTY -> Res.string.validation_email_required
        EmailValidationResult.INVALID_FORMAT, EmailValidationResult.TOO_LONG -> Res.string.validation_email_invalid
    }

/** The name error. */
val NameValidationResult.message: StringResource?
    get() = when (this) {
        NameValidationResult.VALID -> null
        NameValidationResult.EMPTY -> Res.string.validation_name_required
        NameValidationResult.TOO_SHORT -> Res.string.validation_name_too_short
    }

/**
 * The mobile-number error. India's ten-digit rule has its own message; [isRegistration] forms also
 * say when the number is missing.
 */
fun PhoneValidationResult.message(isIndia: Boolean, isRegistration: Boolean): StringResource? = when {
    this == PhoneValidationResult.VALID -> null
    this == PhoneValidationResult.EMPTY && isRegistration -> Res.string.validation_phone_required
    isIndia -> Res.string.validation_phone_india
    else -> Res.string.validation_phone_invalid
}

/** The label for a content type. */
val ShowType.labelResource: StringResource
    get() = when (this) {
        ShowType.VIDEO -> Res.string.content_kind_video
        ShowType.SERIES -> Res.string.content_kind_series
        ShowType.MICRO_SERIES -> Res.string.content_kind_micro_series
    }
