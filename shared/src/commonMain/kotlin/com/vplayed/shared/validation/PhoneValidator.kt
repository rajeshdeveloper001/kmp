package com.vplayed.shared.validation

enum class PhoneValidationResult { VALID, EMPTY, INVALID_FORMAT, TOO_SHORT, TOO_LONG }

/**
 * The mobile-number rule of phone sign-in, on the national number (country code chosen separately):
 * - India (+91): ten digits starting with 6–9 (sign-in prototype, "Enter a valid 10-digit mobile number");
 * - other countries: 7–15 digits (the UIKit app's rule).
 *
 * Spaces and other separators the user types are ignored ([sanitize]).
 */
object PhoneValidator {
    const val MIN_LENGTH = 7
    const val MAX_LENGTH = 15

    private const val INDIA = "91"
    private val indianMobile = Regex("^[6-9]\\d{9}$")

    /** Digits only, at most [MAX_LENGTH]: what the field keeps while typing. */
    fun sanitize(input: String): String = input.filter { it in '0'..'9' }.take(MAX_LENGTH)

    fun validate(dialCode: String, input: String): PhoneValidationResult {
        val digits = input.filter { it in '0'..'9' }
        return when {
            digits.isEmpty() -> PhoneValidationResult.EMPTY
            dialCode == INDIA -> if (indianMobile.matches(digits)) PhoneValidationResult.VALID else PhoneValidationResult.INVALID_FORMAT
            digits.length < MIN_LENGTH -> PhoneValidationResult.TOO_SHORT
            digits.length > MAX_LENGTH -> PhoneValidationResult.TOO_LONG
            else -> PhoneValidationResult.VALID
        }
    }

    fun isValid(dialCode: String, input: String): Boolean = validate(dialCode, input) == PhoneValidationResult.VALID
}
