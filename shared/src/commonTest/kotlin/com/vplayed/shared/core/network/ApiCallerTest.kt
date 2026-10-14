package com.vplayed.shared.core.network

import com.vplayed.shared.core.error.AppError
import com.vplayed.shared.core.error.DataResult
import com.vplayed.shared.core.platform.SharedLogger
import com.vplayed.shared.core.platform.NoOpLogger
import com.vplayed.shared.core.serialization.SharedJson
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiCallerTest {

    private fun caller(recorder: RecordingEngine, logger: SharedLogger = NoOpLogger, logging: Boolean = false) = ApiCaller(
        HttpClientFactory.backend(recorder.engine, testConfig(loggingEnabled = logging), FakeDeviceInfo(), InMemoryRequestCredentials(), logger),
        SharedJson,
    )

    private fun DataResult<VPlayedResponse>.response(): VPlayedResponse = assertIs<DataResult.Success<VPlayedResponse>>(this).data

    @Serializable
    private data class UserDto(val email: String? = null)

    @Test
    fun givenEnvelope_whenParsed_thenStatusesAndMessageAreKept() = runTest {
        val recorder = RecordingEngine {
            respondJson("""{"error":false,"statusCode":203,"status":"success","message":"  Code sent ","response":{"email":"user@example.com"}}""")
        }

        val response = caller(recorder).get("users/api/v1/x").response()

        assertEquals(200, response.httpStatus)
        assertEquals(203, response.effectiveStatusCode)
        assertEquals("Code sent", response.message)
        assertFalse(response.reportsError)
        assertEquals(UserDto("user@example.com"), response.decodeResponse(SharedJson, UserDto.serializer()))
    }

    @Test
    fun givenHttpErrorWithEnvelope_whenParsed_thenItIsAResponseNotAFailure() = runTest {
        val recorder = RecordingEngine { respondJson("""{"error":true,"message":"Invalid OTP"}""", HttpStatusCode.UnprocessableEntity) }

        val response = caller(recorder).get("users/api/v1/x").response()

        assertEquals(422, response.httpStatus)
        assertFalse(response.isHttpSuccess)
        assertEquals(422, response.effectiveStatusCode) // no statusCode in the body: HTTP decides
        assertTrue(response.reportsError)
        assertEquals("Invalid OTP", response.message)
    }

    @Test
    fun givenNonJsonOrOtherShapes_whenParsed_thenNoEnvelope() = runTest {
        listOf("<html>502</html>", "", "[1,2]", "\"text\"").forEach { body ->
            val recorder = RecordingEngine { respond(body, HttpStatusCode.BadGateway) }

            val response = caller(recorder).get("x").response()

            assertNull(response.envelope, body)
            assertEquals(502, response.effectiveStatusCode)
        }
    }

    @Test
    fun givenResponseOfAnotherShape_whenDecoding_thenNull() = runTest {
        val recorder = RecordingEngine { respondJson("""{"statusCode":200,"response":"not an object"}""") }

        assertNull(caller(recorder).get("x").response().decodeResponse(SharedJson, UserDto.serializer()))
    }

    @Test
    fun givenJsonPost_whenSent_thenBodyIsJson() = runTest {
        val recorder = RecordingEngine()

        caller(recorder).postJson("users/api/v1/auth/email-login", buildJsonObject { put("data", "cipher") }, RequestOptions.Sensitive)

        val content = assertIs<TextContent>(recorder.last.body)
        assertEquals(HttpMethod.Post, recorder.last.method)
        assertEquals("""{"data":"cipher"}""", content.text)
        assertEquals(ContentType.Application.Json, content.contentType.withoutParameters())
    }

    @Test
    fun givenFormPost_whenSent_thenFieldsAreFormEncoded() = runTest {
        val recorder = RecordingEngine()

        caller(recorder).postForm("users/api/v1/auth/logout-device", listOf("username" to "u", "preference" to "all"))

        val form = assertIs<FormDataContent>(recorder.last.body)
        assertEquals("u", form.formData["username"])
        assertEquals("all", form.formData["preference"])
    }

    @Test
    fun givenTransportFailures_whenCalling_thenTypedFailures() = runTest {
        val offline = RecordingEngine { throw IOException("offline") }
        val slow = RecordingEngine { throw HttpRequestTimeoutException("https://api.test.invalid/x", 30_000) }

        assertEquals(DataResult.Failure(AppError.Network), caller(offline).get("x"))
        assertEquals(DataResult.Failure(AppError.Timeout), caller(slow).get("x"))
    }

    @Test
    fun givenCancellation_whenCalling_thenItIsRethrown() = runTest {
        val cancelled = RecordingEngine { throw CancellationException("screen closed") }

        assertFailsWith<CancellationException> { caller(cancelled).get("x") }
    }

    @Test
    fun givenAbsolutePath_whenCalling_thenRejectedWithoutARequest() = runTest {
        val recorder = RecordingEngine()

        val result = caller(recorder).get("https://elsewhere.invalid/x")

        assertIs<DataResult.Failure>(result)
        assertTrue(recorder.requests.isEmpty())
    }

    @Test
    fun givenLoggingEnabled_whenRequesting_thenOneLineWithoutSecretsAndRedactedPaths() = runTest {
        val lines = mutableListOf<String>()
        val logger = object : SharedLogger {
            override fun debug(tag: String, message: String) { lines += message }
            override fun info(tag: String, message: String) { lines += message }
            override fun error(tag: String, message: String, throwable: Throwable?) { lines += message }
        }
        val recorder = RecordingEngine()
        val api = caller(recorder, logger, logging = true)

        api.get("mediaview/api/v1/home")
        api.postJson("users/api/v1/auth/verifyEmailOtp", buildJsonObject { put("data", "secret-cipher") }, RequestOptions.Sensitive)

        assertEquals(2, lines.size)
        assertTrue(lines[0].startsWith("GET /mediaview/api/v1/home -> 200"), lines[0])
        assertTrue(lines[1].startsWith("POST <redacted> -> 200"), lines[1])
        assertFalse(lines.any { "secret-cipher" in it })
    }
}
