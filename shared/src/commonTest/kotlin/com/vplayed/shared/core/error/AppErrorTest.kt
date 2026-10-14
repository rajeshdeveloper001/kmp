package com.vplayed.shared.core.error

import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class AppErrorTest {

    @Test
    fun givenExceptions_whenMapped_thenMatchingAppError() {
        assertEquals(AppError.Network, IOException("dns").toAppError())
        assertEquals(AppError.Timeout, HttpRequestTimeoutException("u", 1).toAppError())
        assertEquals(AppError.Parsing, SerializationException("bad").toAppError())
        assertEquals(AppError.Unauthorized, ApiException(401, "expired").toAppError())
        assertEquals(AppError.Server(500, "boom"), ApiException(500, "boom").toAppError())
        assertIs<AppError.Unknown>(IllegalStateException("bug").toAppError())
    }

    @Test
    fun givenBlockSucceeds_whenRunningResultOf_thenSuccess() = runTest {
        assertEquals(DataResult.Success(3), resultOf { 1 + 2 })
    }

    @Test
    fun givenBlockThrows_whenRunningResultOf_thenFailure() = runTest {
        assertEquals(DataResult.Failure(AppError.Network), resultOf<Int> { throw IOException("offline") })
    }

    @Test
    fun givenCancellation_whenRunningResultOf_thenRethrown() = runTest {
        assertFailsWith<CancellationException> { resultOf<Int> { throw CancellationException("cancelled") } }
    }
}
