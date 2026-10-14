package com.vplayed.shared.auth

import com.vplayed.shared.PlatformDependencies
import com.vplayed.shared.SharedGraph
import com.vplayed.shared.core.config.SharedConfig
import com.vplayed.shared.core.crypto.platformCryptoPrimitives
import com.vplayed.shared.core.error.AppError
import com.vplayed.shared.core.network.FakeDeviceInfo
import com.vplayed.shared.core.network.RecordingEngine
import com.vplayed.shared.core.network.testConfig
import com.vplayed.shared.core.platform.NoOpLogger
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.formUrlEncode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.vplayed.shared.session.testSession
import com.vplayed.shared.session.testSessions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Synthetic answers through the real client stack (headers, envelope parsing, encryption) on a MockEngine. */
class AuthRepositoryTest {

    private val answers = ArrayDeque<Pair<Int, String>>()
    private val recorder = RecordingEngine {
        val (status, body) = answers.removeFirst()
        respond(body, HttpStatusCode.fromValue(status), headersOf(HttpHeaders.ContentType, "application/json"))
    }

    private fun graph(config: SharedConfig = testConfig(), deviceId: String? = "device-123") = SharedGraph(
        config,
        PlatformDependencies(
            deviceInfo = FakeDeviceInfo(deviceId = deviceId, deviceName = "Test Pixel"),
            logger = NoOpLogger,
            engineFactory = { recorder.engine },
            cryptoPrimitives = platformCryptoPrimitives(),
        ),
        testSessions(),
    )

    private val graph = graph()
    private val repository = graph.authRepository

    private fun answer(status: Int, body: String) = answers.addLast(status to body)

    private fun HttpRequestData.text(): String = when (val content = body) {
        is TextContent -> content.text
        is FormDataContent -> content.formData.formUrlEncode()
        else -> error("Unexpected body ${content::class}")
    }

    private fun HttpRequestData.decryptedData(): String {
        val wrapper = Json.parseToJsonElement(text()).jsonObject
        assertEquals(setOf("data"), wrapper.keys)
        return graph.transportCipher.decrypt(PASSPHRASE, wrapper.getValue("data").jsonPrimitive.content)!!
    }

    // region email-login

    @Test
    fun givenValidEmail_whenRequesting_thenPostsEncryptedDataWrapperOnce() = runTest {
        answer(200, """{"error":false,"statusCode":200,"status":"success","message":"OTP sent"}""")

        repository.requestEmailLogin("  user@example.com  ")

        val request = recorder.last
        assertEquals("POST", request.method.value)
        assertEquals("/users/api/v1/auth/email-login", request.url.encodedPath)
        assertFalse(EMAIL in request.text(), "Raw email must not be sent")
        // Decrypting once yields the plain email JSON, so it was encrypted exactly once.
        assertEquals("""{"email":"user@example.com"}""", request.decryptedData())
    }

    @Test
    fun given200_whenRequesting_thenCodeSentToExistingUser() = runTest {
        answer(200, """{"error":false,"statusCode":200,"message":"OTP sent to your email"}""")
        assertEquals(EmailLoginResult.CodeSent("OTP sent to your email", isNewUser = false), repository.requestEmailLogin(EMAIL))
    }

    @Test
    fun given203_whenRequesting_thenCodeSentToNewUser() = runTest {
        // Decision D2: a new account goes to the code step too (VPlayed used to stop here).
        answer(203, """{"error":false,"statusCode":203,"message":"Welcome"}""")
        assertEquals(EmailLoginResult.CodeSent("Welcome", isNewUser = true), repository.requestEmailLogin(EMAIL))
    }

    @Test
    fun given202_whenRequesting_thenDeviceLimitReached() = runTest {
        answer(202, """{"error":true,"statusCode":202,"message":"Device limit exceeded"}""")
        assertEquals(EmailLoginResult.DeviceLimitReached("Device limit exceeded", null), repository.requestEmailLogin(EMAIL))
    }

    @Test
    fun givenHttp200WithApiError_whenRequesting_thenRejectedNotSuccess() = runTest {
        answer(200, """{"error":true,"statusCode":422,"message":"Account blocked"}""")
        assertEquals(EmailLoginResult.Rejected("Account blocked"), repository.requestEmailLogin(EMAIL))
        answer(200, """{"error":true,"statusCode":200,"message":"Account blocked"}""")
        assertEquals(EmailLoginResult.Rejected("Account blocked"), repository.requestEmailLogin(EMAIL))
    }

    @Test
    fun given203WithErrorFlag_whenRequesting_thenStatusDecidesAndCodeIsSent() = runTest {
        // Both previous apps act on 203 without reading the body's `error` flag.
        answer(203, """{"error":true,"statusCode":203,"message":"Welcome"}""")
        assertEquals(EmailLoginResult.CodeSent("Welcome", isNewUser = true), repository.requestEmailLogin(EMAIL))
    }

    @Test
    fun givenEnvelopeStatusDiffersFromHttp_whenRequesting_thenEnvelopeDecides() = runTest {
        // Decision D1: within 2xx the envelope's `statusCode` decides.
        answer(200, """{"error":false,"statusCode":203,"message":"Welcome"}""")
        assertEquals(EmailLoginResult.CodeSent("Welcome", isNewUser = true), repository.requestEmailLogin(EMAIL))
        answer(203, """{"error":false,"statusCode":200,"message":"Sent"}""")
        assertEquals(EmailLoginResult.CodeSent("Sent", isNewUser = false), repository.requestEmailLogin(EMAIL))
        answer(200, """{"statusCode":202,"message":"Too many devices"}""")
        assertIs<EmailLoginResult.DeviceLimitReached>(repository.requestEmailLogin(EMAIL))
    }

    @Test
    fun givenNon2xxWithSuccessEnvelope_whenRequesting_thenNotSuccess() = runTest {
        // Decision D1: an HTTP error is never turned into a success by the body.
        answer(500, """{"statusCode":200,"message":"ok"}""")
        assertEquals(EmailLoginResult.Rejected("ok"), repository.requestEmailLogin(EMAIL))
    }

    @Test
    fun givenUnexpected2xx_whenRequesting_thenNotReportedAsSuccess() = runTest {
        answer(201, """{"error":false,"message":"?"}""")
        assertEquals(EmailLoginResult.Failure(AppError.Server(201, "?")), repository.requestEmailLogin(EMAIL))
    }

    @Test
    fun givenEmptyOrMalformedBody_whenRequesting_thenParsingFailure() = runTest {
        answer(200, "")
        assertEquals(EmailLoginResult.Failure(AppError.Parsing), repository.requestEmailLogin(EMAIL))
        answer(200, "<html>oops</html>")
        assertEquals(EmailLoginResult.Failure(AppError.Parsing), repository.requestEmailLogin(EMAIL))
    }

    @Test
    fun givenClientErrorWithMessage_whenRequesting_thenRejectedWithServerMessage() = runTest {
        answer(422, """{"error":true,"statusCode":422,"message":"The email must be a valid email address."}""")
        assertEquals(EmailLoginResult.Rejected("The email must be a valid email address."), repository.requestEmailLogin(EMAIL))
    }

    @Test
    fun givenServerErrorWithoutEnvelope_whenRequesting_thenServerFailure() = runTest {
        answer(500, "Internal Server Error")
        assertEquals(EmailLoginResult.Failure(AppError.Server(500)), repository.requestEmailLogin(EMAIL))
    }

    @Test
    fun givenUnauthorizedWithoutMessage_whenRequesting_thenUnauthorizedFailure() = runTest {
        answer(401, "")
        assertEquals(EmailLoginResult.Failure(AppError.Unauthorized), repository.requestEmailLogin(EMAIL))
    }

    @Test
    fun givenConnectionDrop_whenRequesting_thenNetworkFailure() = runTest {
        val offline = RecordingEngine { throw IOException("connection reset") }
        val repository = SharedGraph(
            testConfig(),
            PlatformDependencies(FakeDeviceInfo(), NoOpLogger, { offline.engine }, platformCryptoPrimitives()),
            testSessions(),
        ).authRepository

        assertEquals(EmailLoginResult.Failure(AppError.Network), repository.requestEmailLogin(EMAIL))
        assertEquals(OtpVerificationResult.Failure(AppError.Network), repository.verifyEmailOtp(EMAIL, "123456"))
    }

    @Test
    fun givenNoTransportKey_whenRequesting_thenFailsWithoutSendingRequest() = runTest {
        val base = testConfig()
        val noKey = SharedConfig(base.environment, base.serverUrl, base.imageBaseUrl, base.loggingEnabled, base.appVersion, base.platform)
        val repository = graph(noKey).authRepository

        assertIs<AppError.Configuration>((repository.requestEmailLogin(EMAIL) as EmailLoginResult.Failure).error)
        assertIs<AppError.Configuration>((repository.verifyEmailOtp(EMAIL, "123456") as OtpVerificationResult.Failure).error)
        assertTrue(recorder.requests.isEmpty())
    }

    // endregion

    // region verifyEmailOtp

    private fun verifiedBody(status: Int = 200, error: String = "false", profileCount: String = "1", firstName: String = "Sam") =
        """{"error":$error,"statusCode":$status,"message":"Login successful","response":{"_id":"u-1","email":"user@example.com",
        "first_name":"$firstName","access_token":"access-1","refresh_token":"refresh-1","default_profile":{"_id":"p-1"},
        "unique_user_id":"42","phone":"","profile_count":$profileCount,"isNewUsers":false}}"""

    private val expectedSession = AuthSession(
        accessToken = "access-1",
        refreshToken = "refresh-1",
        userId = "u-1",
        uniqueUserId = 42,
        email = EMAIL,
        phone = null,
        firstName = "Sam",
        profileId = "p-1",
        isGuest = false,
    )

    @Test
    fun givenCorrectCode_whenVerifying_thenPostsEncryptedEmailCodeAndDeviceName() = runTest {
        answer(200, verifiedBody())

        repository.verifyEmailOtp("  user@example.com ", "123456")

        val request = recorder.last
        assertEquals("/users/api/v1/auth/verifyEmailOtp", request.url.encodedPath)
        assertFalse("123456" in request.text(), "The code must not be sent in clear")
        assertEquals("""{"email":"user@example.com","otp":"123456","deviceDetail":"Test Pixel"}""", request.decryptedData())
    }

    @Test
    fun givenCorrectCode_whenVerified_thenSessionReturned() = runTest {
        answer(200, verifiedBody())

        val result = repository.verifyEmailOtp(EMAIL, "123456")

        assertEquals(OtpVerificationResult.Verified(expectedSession, isNewUser = false, hasMultipleProfiles = false), result)
        // The repository returns the session; storing it is the caller's `SessionManager.signIn`.
        assertNull(graph.sessionManager.session)
    }

    @Test
    fun givenEmailOnlyAccount_whenVerified_thenSignedIn() = runTest {
        // Decision D4: no "linked sign-in" rule (VPlayed used to ask such accounts to register).
        answer(200, """{"statusCode":200,"response":{"access_token":"access-1"}}""")

        val result = repository.verifyEmailOtp(EMAIL, "123456") as OtpVerificationResult.Verified

        assertEquals(EMAIL, result.session.email)
        assertNull(result.session.userId)
        assertTrue(result.isNewUser, "No first name: the profile still has to be completed")
    }

    @Test
    fun givenAccountWithSeveralProfilesOrNoName_whenVerified_thenFlagsReflectIt() = runTest {
        answer(200, verifiedBody(profileCount = "\"3\"", firstName = ""))

        val result = repository.verifyEmailOtp(EMAIL, "123456") as OtpVerificationResult.Verified

        assertTrue(result.isNewUser)
        assertTrue(result.hasMultipleProfiles)
    }

    @Test
    fun givenHttp200WithApiError_whenVerifying_thenRejected() = runTest {
        answer(200, verifiedBody(error = "true").replace("Login successful", "Invalid OTP"))
        assertEquals(OtpVerificationResult.Rejected("Invalid OTP"), repository.verifyEmailOtp(EMAIL, "123456"))
    }

    @Test
    fun givenHttp200WithoutToken_whenVerifying_thenFailureNotLogin() = runTest {
        answer(200, """{"error":false,"message":"ok","response":{"_id":"u-1"}}""")
        assertEquals(OtpVerificationResult.Failure(AppError.Parsing), repository.verifyEmailOtp(EMAIL, "123456"))
        answer(200, """{"error":false,"message":"ok"}""")
        assertEquals(OtpVerificationResult.Failure(AppError.Parsing), repository.verifyEmailOtp(EMAIL, "123456"))
        answer(200, "")
        assertEquals(OtpVerificationResult.Failure(AppError.Parsing), repository.verifyEmailOtp(EMAIL, "123456"))
    }

    @Test
    fun givenInvalidOrExpiredCode_whenServerRefuses_thenRejectedWithServerMessage() = runTest {
        answer(422, """{"error":true,"statusCode":422,"message":"OTP is invalid"}""")
        assertEquals(OtpVerificationResult.Rejected("OTP is invalid"), repository.verifyEmailOtp(EMAIL, "111111"))
        answer(400, """{"error":true,"statusCode":400,"message":"OTP has expired"}""")
        assertEquals(OtpVerificationResult.Rejected("OTP has expired"), repository.verifyEmailOtp(EMAIL, "222222"))
    }

    @Test
    fun givenServerError_whenVerifying_thenServerFailure() = runTest {
        answer(500, "Internal Server Error")
        assertEquals(OtpVerificationResult.Failure(AppError.Server(500)), repository.verifyEmailOtp(EMAIL, "123456"))
    }

    @Test
    fun givenNewAccountWithoutSession_whenVerifying_thenRegistrationRequired() = runTest {
        answer(203, """{"error":false,"statusCode":203,"message":"Please register"}""")
        assertEquals(OtpVerificationResult.RegistrationRequired("Please register"), repository.verifyEmailOtp(EMAIL, "123456"))
        answer(203, """{"error":true,"statusCode":203,"message":"Please register"}""")
        assertEquals(OtpVerificationResult.RegistrationRequired("Please register"), repository.verifyEmailOtp(EMAIL, "123456"))
    }

    @Test
    fun givenNewAccountWithSession_whenVerifying_thenSignedInAsNewUser() = runTest {
        answer(203, verifiedBody(status = 203))
        assertEquals(
            OtpVerificationResult.Verified(expectedSession, isNewUser = true, hasMultipleProfiles = false),
            repository.verifyEmailOtp(EMAIL, "123456"),
        )
    }

    @Test
    fun givenDeviceLimit_whenVerifying_thenDevicesListedWithoutSession() = runTest {
        answer(202, deviceLimitBody())

        val result = repository.verifyEmailOtp(EMAIL, "123456") as OtpVerificationResult.DeviceLimitReached

        assertEquals("Device limit exceeded", result.message)
        val limit = result.limit!!
        assertEquals(2, limit.maxDevices)
        assertEquals(listOf("Chrome on Linux", "iPhone 15"), limit.devices.map { it.name })
        assertEquals(listOf(DeviceKind.DESKTOP, DeviceKind.PHONE), limit.devices.map { it.kind })
        assertEquals(listOf("Chrome", null), limit.devices.map { it.detail })
        assertEquals(listOf(false, true), limit.devices.map { it.isCurrent })
        assertEquals(DeviceSignOutKey("dev-user-1", "dev-token-1"), limit.devices[0].signOutKey)
    }

    @Test
    fun givenDeviceLimit_whenRequestingCode_thenDevicesListed() = runTest {
        answer(202, deviceLimitBody())

        val result = repository.requestEmailLogin(EMAIL) as EmailLoginResult.DeviceLimitReached

        assertEquals(2, result.limit!!.devices.size)
    }

    private fun deviceLimitBody() = """{"error":true,"statusCode":202,"message":"Device limit exceeded","response":{
        "device_limit":"2","get_devices":[
        {"username":"dev-user-1","access_token":"dev-token-1","device_name":"Chrome on Linux","device_type":"web","browser_name":"Chrome","current_device":0},
        {"username":"dev-user-2","access_token":"dev-token-2","device_name":"iPhone 15","device_type":"ios","browser_name":"","current_device":"1"},
        {"device_name":"No credentials","device_type":"web"}]}}"""

    @Test
    fun givenCredentials_whenPrinted_thenMasked() {
        assertFalse("dev-token" in DeviceSignOutKey("dev-user", "dev-token").toString())
        assertFalse("access-1" in expectedSession.toString())
        assertFalse("refresh-1" in expectedSession.toString())
    }

    // endregion

    // region devices

    @Test
    fun givenOneDevice_whenSigningOut_thenPostsItsOwnCredentialsWithPreferenceOne() = runTest {
        answer(200, """{"error":false,"message":"Device logged out"}""")

        val result = repository.signOutDevice(DeviceSignOutKey("dev-user-1", "dev-token-1"), all = false)

        assertEquals(SignOutDeviceResult.Success, result)
        assertEquals("/users/api/v1/auth/logout-device", recorder.last.url.encodedPath)
        assertEquals("username=dev-user-1&access_token=dev-token-1&preference=1", recorder.last.text())
    }

    @Test
    fun givenAllDevices_whenSigningOut_thenPreferenceAll() = runTest {
        answer(200, """{"error":false}""")
        repository.signOutDevice(DeviceSignOutKey("dev-user-1", null), all = true)
        assertEquals("username=dev-user-1&preference=all", recorder.last.text())
    }

    @Test
    fun givenSignOutRefused_whenSigningOut_thenRejectedOrFailure() = runTest {
        answer(200, """{"error":true,"message":"Could not sign out"}""")
        assertEquals(SignOutDeviceResult.Rejected("Could not sign out"), repository.signOutDevice(DeviceSignOutKey("u", "t"), false))
        // Without an explicit `error: false` the previous app does not accept the answer.
        answer(200, """{"message":"?"}""")
        assertIs<SignOutDeviceResult.Rejected>(repository.signOutDevice(DeviceSignOutKey("u", "t"), false))
        answer(500, "")
        assertEquals(SignOutDeviceResult.Failure(AppError.Server(500)), repository.signOutDevice(DeviceSignOutKey("u", "t"), false))
    }

    @Test
    fun givenDevicesSignedOut_whenContinuing_thenPostsFormWithoutCodeAndSignsIn() = runTest {
        answer(200, verifiedBody())

        val result = repository.continueAfterDeviceSignOut(" user@example.com ")

        assertIs<OtpVerificationResult.Verified>(result)
        assertEquals("/users/api/v1/auth/verifyEmailLogin", recorder.last.url.encodedPath)
        assertEquals(
            "is_email_verified=1&email=user%40example.com&deviceDetail=Test+Pixel&browsername=",
            recorder.last.text(),
        )
    }

    @Test
    fun givenDeviceTypes_whenMapping_thenIconKind() {
        assertEquals(DeviceKind.PHONE, DefaultAuthRepository.deviceKindOf("android"))
        assertEquals(DeviceKind.TV, DefaultAuthRepository.deviceKindOf("Android TV"))
        assertEquals(DeviceKind.DESKTOP, DefaultAuthRepository.deviceKindOf("Web"))
        assertEquals(DeviceKind.OTHER, DefaultAuthRepository.deviceKindOf(null))
        assertEquals(DeviceKind.OTHER, DefaultAuthRepository.deviceKindOf("toaster"))
    }

    // endregion

    // region guest and logout

    @Test
    fun givenDevice_whenJoiningAsGuest_thenPostsDeviceIdAndReturnsGuestSession() = runTest {
        answer(200, """{"statusCode":200,"response":{"access_token":"guest-1","is_guest_user":0,"guest_user_id":"7","unique_user_id":9}}""")

        val result = repository.guestLogin() as GuestLoginResult.SignedIn

        assertEquals("/users/api/v1/auth/generate", recorder.last.url.encodedPath)
        assertEquals("""{"uuid":"device-123"}""", recorder.last.text())
        assertTrue(result.session.isGuest, "This endpoint only issues guest sessions")
        assertEquals(7L, result.session.uniqueUserId)
        assertNull(result.session.email)
    }

    @Test
    fun givenNoTokenOrError_whenJoiningAsGuest_thenNoSession() = runTest {
        answer(200, """{"statusCode":200,"response":{}}""")
        assertEquals(GuestLoginResult.Failure(AppError.Parsing), repository.guestLogin())
        answer(200, """{"statusCode":429,"message":"Try later"}""")
        assertEquals(GuestLoginResult.Rejected("Try later"), repository.guestLogin())
    }

    @Test
    fun givenNoDeviceId_whenJoiningAsGuest_thenNothingSent() = runTest {
        val result = graph(deviceId = null).authRepository.guestLogin() as GuestLoginResult.Failure

        assertIs<AppError.Configuration>(result.error)
        assertTrue(recorder.requests.isEmpty())
    }

    @Test
    fun givenSignedIn_whenLoggingOut_thenPostsEmptyBodyWithTheSessionToken() = runTest {
        graph.sessionManager.signIn(testSession("access-1"))
        answer(200, """{"statusCode":200,"message":"Logged out"}""")

        assertEquals(LogoutResult.LoggedOut, repository.logout())
        assertEquals("/users/api/v1/auth/logout", recorder.last.url.encodedPath)
        assertEquals("{}", recorder.last.text())
        assertEquals("Bearer access-1", recorder.last.headers[HttpHeaders.Authorization])
    }

    @Test
    fun givenLogoutAnswers_whenLoggingOut_thenMapped() = runTest {
        answer(200, """{"statusCode":203}""")
        assertEquals(LogoutResult.LoggedOut, repository.logout())
        answer(401, "")
        assertEquals(LogoutResult.SessionAlreadyEnded, repository.logout())
        answer(200, """{"statusCode":403,"message":"Forbidden"}""")
        assertEquals(LogoutResult.SessionAlreadyEnded, repository.logout())
        answer(200, """{"statusCode":500,"message":"Try again"}""")
        assertEquals(LogoutResult.Rejected("Try again"), repository.logout())
        answer(502, "Bad Gateway")
        assertEquals(LogoutResult.Failure(AppError.Server(502)), repository.logout())
    }

    // endregion

    private companion object {
        const val EMAIL = "user@example.com"
        const val PASSPHRASE = "unit-test-passphrase"
    }
}
