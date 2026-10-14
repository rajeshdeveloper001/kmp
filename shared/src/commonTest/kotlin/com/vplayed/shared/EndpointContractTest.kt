package com.vplayed.shared

import com.vplayed.shared.auth.DeviceSignOutKey
import com.vplayed.shared.auth.EmailLoginResult
import com.vplayed.shared.auth.GuestLoginResult
import com.vplayed.shared.auth.LogoutResult
import com.vplayed.shared.auth.OtpVerificationResult
import com.vplayed.shared.auth.SignOutDeviceResult
import com.vplayed.shared.core.crypto.platformCryptoPrimitives
import com.vplayed.shared.core.error.AppError
import com.vplayed.shared.core.error.DataResult
import com.vplayed.shared.core.network.FakeDeviceInfo
import com.vplayed.shared.core.network.RecordingEngine
import com.vplayed.shared.core.network.testConfig
import com.vplayed.shared.core.platform.SharedLogger
import com.vplayed.shared.geo.GeoRefreshOutcome
import com.vplayed.shared.home.HomeFixtures
import com.vplayed.shared.session.testSession
import com.vplayed.shared.session.testSessions
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The backend contract of every endpoint the shared layer calls (KMP-602), through the real graph
 * (headers, logging, session, encryption) on a scripted engine. Each endpoint meets the same
 * transport and server scenarios, and the table states what the app sees for each: endpoint-specific
 * answers (200 / 202 / 203 rules, row shapes) are covered by the repository tests.
 *
 * Also checked for every endpoint and scenario: nothing throws; method and path; decision D21 (a 401
 * ends the session on data endpoints only); no token, email, code, IP or ciphertext in the log.
 */
class EndpointContractTest {

    /** What the caller can do with the answer, whatever the endpoint's result type. */
    private enum class Outcome { SUCCESS, NETWORK, TIMEOUT, REFUSED, PARSING, UNAUTHORIZED, RETRYABLE, INVALID }

    private enum class Scenario(val status: Int, val body: String?) {
        OFFLINE(0, null),
        TIMEOUT(0, null),
        HTML_502(502, "<html><body>Bad gateway</body></html>"),
        ENVELOPE_500(500, """{"error":true,"statusCode":500,"message":"Down for maintenance"}"""),
        MALFORMED_200(200, "{not json"),
        UNAUTHORIZED_401(401, ""),
    }

    private class Endpoint(
        val name: String,
        val method: HttpMethod,
        val path: String,
        val successBody: String,
        /** True for the auth endpoints: a 401 there is about the credentials in the body (D21). */
        val keepsSessionOn401: Boolean,
        val expected: Map<Scenario, Outcome>,
        val call: suspend (SharedGraph) -> Any,
    )

    private val dataDefaults = mapOf(
        Scenario.OFFLINE to Outcome.NETWORK,
        Scenario.TIMEOUT to Outcome.TIMEOUT,
        Scenario.HTML_502 to Outcome.REFUSED,
        Scenario.ENVELOPE_500 to Outcome.REFUSED,
        Scenario.MALFORMED_200 to Outcome.PARSING,
        Scenario.UNAUTHORIZED_401 to Outcome.UNAUTHORIZED,
    )

    private val endpoints = listOf(
        Endpoint("email-login", HttpMethod.Post, "/users/api/v1/auth/email-login", """{"statusCode":200,"message":"Code sent"}""", true, dataDefaults) {
            it.authRepository.requestEmailLogin(EMAIL)
        },
        Endpoint("verifyEmailOtp", HttpMethod.Post, "/users/api/v1/auth/verifyEmailOtp", VERIFIED, true, dataDefaults) {
            it.authRepository.verifyEmailOtp(EMAIL, CODE)
        },
        Endpoint("verifyEmailLogin", HttpMethod.Post, "/users/api/v1/auth/verifyEmailLogin", VERIFIED, true, dataDefaults) {
            it.authRepository.continueAfterDeviceSignOut(EMAIL)
        },
        Endpoint("logout-device", HttpMethod.Post, "/users/api/v1/auth/logout-device", """{"error":false,"message":"Device logged out"}""", true, dataDefaults) {
            it.authRepository.signOutDevice(DeviceSignOutKey(OTHER_USER, OTHER_TOKEN), all = false)
        },
        Endpoint("generate (guest)", HttpMethod.Post, "/users/api/v1/auth/generate", GUEST, true, dataDefaults) {
            it.authRepository.guestLogin()
        },
        Endpoint("logout", HttpMethod.Post, "/users/api/v1/auth/logout", """{"statusCode":200}""", true, dataDefaults) {
            it.authRepository.logout()
        },
        Endpoint("homebanners", HttpMethod.Get, "/mediaview/api/v1/homebanners", HomeFixtures.BANNERS, false, dataDefaults) {
            it.homeRepository.getBanners()
        },
        Endpoint("home", HttpMethod.Get, "/mediaview/api/v1/home", HomeFixtures.HOME_PAGE_1, false, dataDefaults) {
            it.homeRepository.getSections(page = 1)
        },
        Endpoint("home/all/{id}", HttpMethod.Get, "/mediaview/api/v1/home/all/row-1", HomeFixtures.SECTION_ITEMS_PAGE_2, false, dataDefaults) {
            it.homeRepository.getSectionItems("row-1", page = 2)
        },
        Endpoint("follow_studio", HttpMethod.Post, "/studios/api/v1/follow_studio", """{"statusCode":200,"message":"Followed"}""", false, dataDefaults) {
            it.homeRepository.setFollowing("studio-1", follow = true)
        },
        Endpoint("common", HttpMethod.Get, "/mediaview/api/v1/common", """{"statusCode":200,"response":{"content_library":1}}""", false, dataDefaults) {
            it.commonSettingsRepository.load()
        },
        Endpoint(
            "geo-data/{ip}", HttpMethod.Get, "/mediaview/api/v1/common/geo-data/$TEST_IP",
            """{"error":false,"response":{"geo_token":"$GEO_TOKEN"}}""", false,
            // The geo refresh reports whether a retry can help (decision D18: the apps decide when).
            mapOf(
                Scenario.OFFLINE to Outcome.RETRYABLE,
                Scenario.TIMEOUT to Outcome.RETRYABLE,
                Scenario.HTML_502 to Outcome.RETRYABLE,
                Scenario.ENVELOPE_500 to Outcome.RETRYABLE,
                Scenario.MALFORMED_200 to Outcome.INVALID,
                Scenario.UNAUTHORIZED_401 to Outcome.INVALID,
            ),
        ) { it.geoRepository.refresh() },
    )

    private val logLines = mutableListOf<String>()
    private val logger = object : SharedLogger {
        override fun debug(tag: String, message: String) { logLines += message }
        override fun info(tag: String, message: String) { logLines += message }
        override fun error(tag: String, message: String, throwable: Throwable?) { logLines += message }
    }

    /** A graph signed in as [testSession] whose backend answers [scenario] (or success); the IP lookup always works. */
    private fun graph(endpoint: Endpoint, scenario: Scenario?): Pair<SharedGraph, RecordingEngine> {
        val recorder = RecordingEngine { request ->
            if (request.url.host == "api.ipify.org") return@RecordingEngine respond("""{"ip":"$TEST_IP"}""", HttpStatusCode.OK, JSON)
            when (scenario) {
                null -> respond(endpoint.successBody, HttpStatusCode.OK, JSON)
                Scenario.OFFLINE -> throw IOException("offline")
                Scenario.TIMEOUT -> throw HttpRequestTimeoutException(request.url.toString(), 30_000)
                else -> respond(scenario.body.orEmpty(), HttpStatusCode.fromValue(scenario.status), JSON)
            }
        }
        val graph = SharedGraph(
            testConfig(loggingEnabled = true),
            PlatformDependencies(FakeDeviceInfo(), logger, { recorder.engine }, platformCryptoPrimitives()),
            testSessions(stored = testSession(SESSION_TOKEN)),
        )
        return graph to recorder
    }

    private fun outcome(result: Any): Outcome = when (result) {
        is DataResult.Success<*> -> Outcome.SUCCESS
        is DataResult.Failure -> outcome(result.error)
        is EmailLoginResult.CodeSent, is EmailLoginResult.DeviceLimitReached -> Outcome.SUCCESS
        is EmailLoginResult.Rejected -> Outcome.REFUSED
        is EmailLoginResult.Failure -> outcome(result.error)
        is OtpVerificationResult.Verified -> Outcome.SUCCESS
        is OtpVerificationResult.Rejected -> Outcome.REFUSED
        is OtpVerificationResult.Failure -> outcome(result.error)
        is OtpVerificationResult.RegistrationRequired, is OtpVerificationResult.DeviceLimitReached -> Outcome.SUCCESS
        SignOutDeviceResult.Success -> Outcome.SUCCESS
        is SignOutDeviceResult.Rejected -> Outcome.REFUSED
        is SignOutDeviceResult.Failure -> outcome(result.error)
        is GuestLoginResult.SignedIn -> Outcome.SUCCESS
        is GuestLoginResult.Rejected -> Outcome.REFUSED
        is GuestLoginResult.Failure -> outcome(result.error)
        LogoutResult.LoggedOut -> Outcome.SUCCESS
        LogoutResult.SessionAlreadyEnded -> Outcome.UNAUTHORIZED
        is LogoutResult.Rejected -> Outcome.REFUSED
        is LogoutResult.Failure -> outcome(result.error)
        GeoRefreshOutcome.SUCCESS -> Outcome.SUCCESS
        GeoRefreshOutcome.TRANSIENT_FAILURE -> Outcome.RETRYABLE
        GeoRefreshOutcome.INVALID_RESPONSE -> Outcome.INVALID
        else -> fail("Unclassified result ${result::class.simpleName}")
    }

    private fun outcome(error: AppError): Outcome = when (error) {
        AppError.Network -> Outcome.NETWORK
        AppError.Timeout -> Outcome.TIMEOUT
        AppError.Unauthorized -> Outcome.UNAUTHORIZED
        is AppError.Server -> Outcome.REFUSED
        AppError.Parsing -> Outcome.PARSING
        is AppError.Configuration, is AppError.Unknown -> fail("Unexpected $error")
    }

    @Test
    fun givenEveryEndpoint_whenTheServerAnswersNormally_thenSuccessWithTheRightMethodAndPath() = runTest {
        for (endpoint in endpoints) {
            val (graph, recorder) = graph(endpoint, scenario = null)

            assertEquals(Outcome.SUCCESS, outcome(endpoint.call(graph)), endpoint.name)

            val sent = recorder.requests.last()
            assertEquals(endpoint.method, sent.method, endpoint.name)
            assertEquals(endpoint.path, sent.url.encodedPath, endpoint.name)
            assertEquals("Bearer $SESSION_TOKEN", sent.headers[HttpHeaders.Authorization], endpoint.name)
        }
    }

    @Test
    fun givenEveryEndpoint_whenEachFailureHappens_thenTheExpectedTypedOutcomeAndNeverAnException() = runTest {
        val mismatches = mutableListOf<String>()
        for (endpoint in endpoints) {
            for (scenario in Scenario.entries) {
                val (graph, _) = graph(endpoint, scenario)
                val actual = try {
                    outcome(endpoint.call(graph))
                } catch (error: Throwable) {
                    mismatches += "${endpoint.name} / $scenario threw ${error::class.simpleName}"
                    continue
                }
                val expected = endpoint.expected.getValue(scenario)
                if (actual != expected) mismatches += "${endpoint.name} / $scenario: expected $expected, got $actual"
            }
        }
        assertTrue(mismatches.isEmpty(), mismatches.joinToString("\n"))
    }

    @Test
    fun givenSignedIn_whenAnEndpointAnswers401_thenOnlyDataEndpointsEndTheSession() = runTest {
        for (endpoint in endpoints) {
            val (graph, _) = graph(endpoint, Scenario.UNAUTHORIZED_401)

            endpoint.call(graph)

            val kept = graph.sessionManager.session?.accessToken == SESSION_TOKEN
            assertEquals(endpoint.keepsSessionOn401, kept, "${endpoint.name}: session kept after 401")
        }
    }

    @Test
    fun givenLoggingOn_whenEveryEndpointRunsEveryScenario_thenNoSecretOrPersonalDataIsLogged() = runTest {
        for (endpoint in endpoints) {
            for (scenario in listOf(null) + Scenario.entries) {
                val (graph, _) = graph(endpoint, scenario)
                endpoint.call(graph)
            }
        }
        assertTrue(logLines.isNotEmpty(), "logging is on, so requests are logged")
        val secrets = listOf(SESSION_TOKEN, EMAIL, CODE, TEST_IP, GEO_TOKEN, OTHER_USER, OTHER_TOKEN, "access-1", "guest-1", "refresh-1")
        val leaks = logLines.filter { line -> secrets.any { it in line } }
        assertTrue(leaks.isEmpty(), leaks.joinToString("\n"))
    }

    private companion object {
        val JSON = headersOf(HttpHeaders.ContentType, "application/json")
        const val EMAIL = "user@example.com"
        const val CODE = "123456"
        const val SESSION_TOKEN = "session-token-1"
        const val OTHER_USER = "other@example.com"
        const val OTHER_TOKEN = "other-device-token"
        const val TEST_IP = "203.0.113.10"
        const val GEO_TOKEN = "eyJjdXJyZW5jeSI6IlVTRCJ9"
        const val VERIFIED = """{"error":false,"statusCode":200,"message":"Login successful","response":{"_id":"u-1",
            "email":"user@example.com","access_token":"access-1","refresh_token":"refresh-1","default_profile":{"_id":"p-1"}}}"""
        const val GUEST = """{"statusCode":200,"response":{"access_token":"guest-1","is_guest_user":1,"guest_user_id":77}}"""
    }
}
