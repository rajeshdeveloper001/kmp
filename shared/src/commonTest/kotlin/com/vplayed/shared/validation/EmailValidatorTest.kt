package com.vplayed.shared.validation

import kotlin.test.Test
import kotlin.test.assertEquals

class EmailValidatorTest {

    @Test
    fun givenWellFormedAddresses_whenValidating_thenValid() {
        listOf("user@example.com", "first.last+tag@sub.example.co.in", "  padded@example.org  ", "a_b%c-d@x.io").forEach {
            assertEquals(EmailValidationResult.VALID, EmailValidator.validate(it), it)
        }
    }

    @Test
    fun givenBlankInput_whenValidating_thenEmpty() {
        listOf("", "   ", "\t\n").forEach { assertEquals(EmailValidationResult.EMPTY, EmailValidator.validate(it)) }
    }

    @Test
    fun givenMalformedAddresses_whenValidating_thenInvalidFormat() {
        listOf("plainaddress", "@example.com", "user@", "user@example", "user@example.c", "user@exa mple.com", "user@@example.com").forEach {
            assertEquals(EmailValidationResult.INVALID_FORMAT, EmailValidator.validate(it), it)
        }
    }

    @Test
    fun givenMoreThan50Characters_whenValidating_thenTooLong() {
        val atLimit = "u".repeat(EmailValidator.MAX_LENGTH - "@example.com".length) + "@example.com"

        assertEquals(EmailValidationResult.VALID, EmailValidator.validate(atLimit))
        assertEquals(EmailValidationResult.TOO_LONG, EmailValidator.validate("u$atLimit"))
    }
}
