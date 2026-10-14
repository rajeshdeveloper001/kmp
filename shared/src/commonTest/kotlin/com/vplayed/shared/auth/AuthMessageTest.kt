package com.vplayed.shared.auth

import com.vplayed.shared.core.error.AppError
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthMessageTest {

    @Test
    fun givenShortPlainServerText_whenChoosing_thenServerTextTrimmed() {
        assertEquals(AuthMessage.Server("Account blocked"), AuthMessage.serverOr("  Account blocked ", AuthMessageKey.LOGIN_FAILED))
    }

    @Test
    fun givenMissingOrUnsafeServerText_whenChoosing_thenFallbackKey() {
        val fallback = AuthMessage.Localized(AuthMessageKey.LOGIN_FAILED)
        listOf(null, "", "   ", "<b>error</b>", "line 1\nline 2", "x".repeat(201)).forEach {
            assertEquals(fallback, AuthMessage.serverOr(it, AuthMessageKey.LOGIN_FAILED), "$it")
        }
    }

    @Test
    fun givenFailures_whenMapped_thenAppKeysNeverServerText() {
        assertEquals(AuthMessage.Localized(AuthMessageKey.ERROR_NETWORK), AuthMessage.from(AppError.Network))
        assertEquals(AuthMessage.Localized(AuthMessageKey.ERROR_TIMEOUT), AuthMessage.from(AppError.Timeout))
        assertEquals(AuthMessage.Localized(AuthMessageKey.ERROR_UNAUTHORIZED), AuthMessage.from(AppError.Unauthorized))
        assertEquals(AuthMessage.Localized(AuthMessageKey.ERROR_SERVER), AuthMessage.from(AppError.Server(500, "stack trace")))
        assertEquals(AuthMessage.Localized(AuthMessageKey.ERROR_PARSING), AuthMessage.from(AppError.Parsing))
        assertEquals(AuthMessage.Localized(AuthMessageKey.ERROR_UNKNOWN), AuthMessage.from(AppError.Configuration("no key")))
        assertEquals(AuthMessage.Localized(AuthMessageKey.ERROR_UNKNOWN), AuthMessage.from(AppError.Unknown()))
    }
}
