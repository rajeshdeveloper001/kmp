package com.vplayed.shared.validation

enum class NameValidationResult { VALID, EMPTY, TOO_SHORT }

/**
 * The name rule of the sign-up form:
 * - letters, digits and spaces only, at most [MAX_LENGTH] characters — the UIKit
 *   `RegisterViewController` refuses any other input ([sanitize]);
 * - required ("User Name is Required") and at least [MIN_LENGTH] characters once trimmed
 *   (prototype "Complete your profile": "Name must be at least 2 characters.").
 */
object NameValidator {
    const val MIN_LENGTH = 2
    const val MAX_LENGTH = 30

    /** What the field keeps while typing: ASCII letters, digits and spaces, at most [MAX_LENGTH]. */
    fun sanitize(input: String): String =
        input.filter { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == ' ' }.take(MAX_LENGTH)

    fun validate(input: String): NameValidationResult {
        val name = input.trim()
        return when {
            name.isEmpty() -> NameValidationResult.EMPTY
            name.length < MIN_LENGTH -> NameValidationResult.TOO_SHORT
            else -> NameValidationResult.VALID
        }
    }
}
