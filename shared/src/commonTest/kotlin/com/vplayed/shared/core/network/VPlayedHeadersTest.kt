package com.vplayed.shared.core.network

import com.vplayed.shared.core.platform.NoOpLogger
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VPlayedHeadersTest {

    private val recorder = RecordingEngine()
    private val device = FakeDeviceInfo()
    private val credentials = InMemoryRequestCredentials()
    private val client = HttpClientFactory.backend(recorder.engine, testConfig(), device, credentials, NoOpLogger)
    private val api = ApiCaller(client, com.vplayed.shared.core.serialization.SharedJson)

    @Test
    fun givenSignedOut_whenRequesting_thenCommonHeadersOnly() = runTest {
        api.get("mediaview/api/v1/homebanners")

        val headers = recorder.last.headers
        assertEquals("application/json", headers[HttpHeaders.Accept])
        assertEquals("mobile", headers["X-Request-Type"])
        assertEquals("android", headers["device_type"])
        assertEquals("3.0.0", headers["app_version"])
        assertEquals("ta", headers["X-Language-Code"])
        assertEquals("device-123", headers["device_id"])
        assertNull(headers[HttpHeaders.Authorization])
        assertNull(headers["profile_id"])
        assertNull(headers["x-bearer-token"])
    }

    @Test
    fun givenRelativePath_whenRequesting_thenServerUrlIsTheBase() = runTest {
        api.get("mediaview/api/v1/home", query = listOf("page" to "2", "limit" to "3"))

        assertEquals("https://api.test.invalid/mediaview/api/v1/home?page=2&limit=3", recorder.last.url.toString())
    }

    @Test
    fun givenSession_whenRequesting_thenAuthorizationAndProfileAreSent() = runTest {
        credentials.setAuthTokens(AuthTokens(accessToken = "access-abc", profileId = "profile-1"))

        api.get("mediaview/api/v1/home")

        assertEquals("Bearer access-abc", recorder.last.headers[HttpHeaders.Authorization])
        assertEquals("profile-1", recorder.last.headers["profile_id"])
    }

    @Test
    fun givenSessionWithoutProfile_whenRequesting_thenNoProfileHeader() = runTest {
        credentials.setAuthTokens(AuthTokens(accessToken = "access-abc", profileId = " "))

        api.get("mediaview/api/v1/home")

        assertEquals("Bearer access-abc", recorder.last.headers[HttpHeaders.Authorization])
        assertNull(recorder.last.headers["profile_id"])
    }

    @Test
    fun givenSignOut_whenRequesting_thenAuthorizationStops() = runTest {
        credentials.setAuthTokens(AuthTokens("access-abc"))
        credentials.setAuthTokens(null)

        api.get("mediaview/api/v1/home")

        assertNull(recorder.last.headers[HttpHeaders.Authorization])
    }

    @Test
    fun givenGeoToken_whenRequesting_thenSentUnlessTheRequestOptsOut() = runTest {
        credentials.setGeoToken("geo-xyz")

        api.get("mediaview/api/v1/home")
        assertEquals("geo-xyz", recorder.last.headers["x-bearer-token"])

        api.get("mediaview/api/v1/common/geo-data/203.0.113.10", options = RequestOptions(sendGeoToken = false, redact = true))
        assertNull(recorder.last.headers["x-bearer-token"])
    }

    @Test
    fun givenBlankGeoToken_whenSet_thenLastValidTokenIsKept() = runTest {
        credentials.setGeoToken("geo-xyz")
        credentials.setGeoToken("  ")

        api.get("mediaview/api/v1/home")

        assertEquals("geo-xyz", recorder.last.headers["x-bearer-token"])
    }

    @Test
    fun givenNoDeviceIdAndBlankLanguage_whenRequesting_thenNoDeviceIdAndEnglish() = runTest {
        val bareRecorder = RecordingEngine()
        val bareClient = HttpClientFactory.backend(
            bareRecorder.engine, testConfig(), FakeDeviceInfo(deviceId = null, language = " "), credentials, NoOpLogger,
        )

        bareClient.get("mediaview/api/v1/home")

        assertNull(bareRecorder.last.headers["device_id"])
        assertEquals("en", bareRecorder.last.headers["X-Language-Code"])
    }

    @Test
    fun givenLanguageChange_whenRequesting_thenNextRequestUsesIt() = runTest {
        device.language = "ar"

        api.get("mediaview/api/v1/home")

        assertEquals("ar", recorder.last.headers["X-Language-Code"])
    }

    @Test
    fun givenThirdPartyClient_whenRequesting_thenNoVPlayedHeadersLeave() = runTest {
        credentials.setAuthTokens(AuthTokens("access-abc", "profile-1"))
        credentials.setGeoToken("geo-xyz")
        val thirdPartyRecorder = RecordingEngine { respondJson("""{"ip":"203.0.113.10"}""") }
        val thirdParty = HttpClientFactory.thirdParty(thirdPartyRecorder.engine, testConfig(), NoOpLogger)

        thirdParty.get("https://api.ipify.org/?format=json")

        val names = thirdPartyRecorder.last.headers.names().map { it.lowercase() }
        listOf("authorization", "profile_id", "x-bearer-token", "device_id", "device_type", "app_version").forEach {
            assertFalse(it in names, "$it must not be sent to third parties")
        }
        assertTrue(thirdPartyRecorder.last.url.host == "api.ipify.org")
    }

    @Test
    fun givenTokens_whenPrinted_thenSecretsAreMasked() {
        val printed = AuthTokens("access-abc", "profile-1").toString()

        assertFalse("access-abc" in printed)
        assertFalse("profile-1" in printed)
    }
}
