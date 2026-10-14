package com.vplayed.shared.validation

enum class EmailValidationResult { VALID, EMPTY, INVALID_FORMAT, TOO_LONG }

/**
 * The email rule for every form. Input is trimmed first.
 *
 * Decision D16 (KMP_PLANNING/KMP_ARCHITECTURE_ANALYSIS.md §5), used by both apps: VPlayed's
 * (UIKit-parity) pattern — local part, '@', domain with a 2–64 letter TLD — and its 50-character
 * limit. AndroidJP used to accept any TLD length with no limit.
 */
object EmailValidator {
    const val MAX_LENGTH = 50

    private val pattern = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,64}$")

    fun validate(input: String): EmailValidationResult {
        val email = input.trim()
        return when {
            email.isEmpty() -> EmailValidationResult.EMPTY
            email.length > MAX_LENGTH -> EmailValidationResult.TOO_LONG
            !pattern.matches(email) -> EmailValidationResult.INVALID_FORMAT
            else -> EmailValidationResult.VALID
        }
    }

    fun isValid(input: String): Boolean = validate(input) == EmailValidationResult.VALID
}
