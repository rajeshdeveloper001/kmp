package com.vplayed.shared.session

import com.vplayed.shared.PlatformDependencies
import com.vplayed.shared.SharedGraph
import com.vplayed.shared.auth.AuthSession
import com.vplayed.shared.auth.LogoutResult
import com.vplayed.shared.core.crypto.platformCryptoPrimitives
import com.vplayed.shared.core.error.AppError
import com.vplayed.shared.core.error.DataResult
import com.vplayed.shared.core.network.FakeDeviceInfo
import com.vplayed.shared.core.network.RecordingEngine
import com.vplayed.shared.core.network.VPlayedHeaderNames
import com.vplayed.shared.core.network.VPlayedResponse
import com.vplayed.shared.core.network.requireResponse
import com.vplayed.shared.core.network.testConfig
import com.vplayed.shared.core.platform.NoOpLogger
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The session as the backend sees it: headers, the 401 rule (decision D21) and logout. */
class SessionGraphTest {

    private val answers = ArrayDeque<Pair<Int, String>>()
    private var duringRequest: suspend () -> Unit = {}
    private val recorder = RecordingEngine {
        duringRequest()
        val (status, body) = answers.removeFirstOrNull() ?: (200 to """{"statusCode":200,"response":{}}""")
        respond(body, HttpStatusCode.fromValue(status), headersOf(HttpHeaders.ContentType, "application/json"))
    }
    private var store = InMemorySecureStore()

    private fun graph(stored: AuthSession? = null): SharedGraph {
        if (stored != null) store = storeHolding(stored)
        return SharedGraph(
            testConfig(),
            PlatformDependencies(FakeDeviceInfo(), NoOpLogger, { recorder.engine }, platformCryptoPrimitives()),
            SessionManager(store),
        )
    }

    private fun answer(status: Int, body: String) = answers.addLast(status to body)

    private suspend fun SharedGraph.home() = apiCaller.get("mediaview/api/v1/home")

    @Test
    fun givenStoredSession_whenTheFirstRequestIsSent_thenItCarriesTheSessionWithoutWaitingExplicitly() = runTest {
        val graph = graph(stored = testSession("access-1", profileId = "profile-1"))

        graph.home()

        assertEquals("Bearer access-1", recorder.last.headers[HttpHeaders.Authorization])
        assertEquals("profile-1", recorder.last.headers[VPlayedHeaderNames.PROFILE_ID])
    }

    @Test
    fun givenSignedOut_whenRequesting_thenNoSessionHeaders() = runTest {
        val graph = graph()

        graph.home()

        assertNull(recorder.last.headers[HttpHeaders.Authorization])
        assertNull(recorder.last.headers[VPlayedHeaderNames.PROFILE_ID])
    }

    @Test
    fun givenSession_whenTheServerAnswersHttp401_thenTheSessionEndsAsExpired() = runTest {
        val graph = graph(stored = testSession("access-1"))
        answer(401, """{"statusCode":401,"message":"Unauthorized"}""")

        val result = graph.home()

        // The caller still gets the answer and reads it as usual.
        assertEquals(DataResult.Failure(AppError.Unauthorized), assertIs<DataResult.Success<VPlayedResponse>>(result).data.requireResponse())
        assertEquals(SessionStatus.SignedOut(SignOutReason.EXPIRED), graph.sessionManager.status.value)
        assertNull(store.peek(SecureKey.SESSION))
        graph.home()
        assertNull(recorder.last.headers[HttpHeaders.Authorization], "Later requests go without the rejected token")
    }

    @Test
    fun givenSession_whenTheEnvelopeSays401_thenTheSessionEnds() = runTest {
        val graph = graph(stored = testSession("access-1"))
        answer(200, """{"statusCode":401,"message":"Token expired"}""")

        graph.home()

        assertEquals(SessionStatus.SignedOut(SignOutReason.EXPIRED), graph.sessionManager.status.value)
    }

    @Test
    fun givenSession_whenAnAuthEndpointAnswers401_thenTheSessionIsKept() = runTest {
        val graph = graph(stored = testSession("guest-1", isGuest = true))
        answer(401, """{"statusCode":401,"message":"Invalid code"}""")

        graph.authRepository.verifyEmailOtp("user@example.com", "000000")

        assertEquals("guest-1", graph.sessionManager.session?.accessToken)
    }

    @Test
    fun givenANewSignInDuringARequest_whenTheOldTokenIsRejected_thenTheNewSessionIsKept() = runTest {
        val graph = graph(stored = testSession("access-1"))
        duringRequest = {
            duringRequest = {}
            graph.sessionManager.signIn(testSession("access-2"))
        }
        answer(401, "")

        graph.home()

        assertEquals("access-2", graph.sessionManager.session?.accessToken)
    }

    @Test
    fun givenLogoutAccepted_whenLoggingOut_thenSignedOutAndStoreCleared() = runTest {
        val graph = graph(stored = testSession())
        answer(200, """{"statusCode":200,"message":"Logged out"}""")

        assertEquals(LogoutResult.LoggedOut, graph.logout())

        assertEquals(SessionStatus.SignedOut(SignOutReason.USER), graph.sessionManager.status.value)
        assertNull(store.peek(SecureKey.SESSION))
    }

    @Test
    fun givenTokenAlreadyRejected_whenLoggingOut_thenSignedOut() = runTest {
        val graph = graph(stored = testSession())
        answer(401, "")

        assertEquals(LogoutResult.SessionAlreadyEnded, graph.logout())

        assertEquals(SessionStatus.SignedOut(SignOutReason.USER), graph.sessionManager.status.value)
    }

    @Test
    fun givenServerError_whenLoggingOut_thenTheSessionIsKeptForARetry() = runTest {
        val graph = graph(stored = testSession())
        answer(500, """{"statusCode":500,"message":"Down"}""")

        val result = graph.logout()

        assertTrue(result is LogoutResult.Rejected || result is LogoutResult.Failure, result.toString())

        assertEquals(testSession(), graph.sessionManager.session)
    }
}
