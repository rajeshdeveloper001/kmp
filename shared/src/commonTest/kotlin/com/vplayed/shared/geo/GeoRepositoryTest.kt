package com.vplayed.shared.geo

import com.vplayed.shared.PlatformDependencies
import com.vplayed.shared.SharedGraph
import com.vplayed.shared.core.crypto.platformCryptoPrimitives
import com.vplayed.shared.core.network.FakeDeviceInfo
import com.vplayed.shared.core.network.RecordingEngine
import com.vplayed.shared.core.network.testConfig
import com.vplayed.shared.core.platform.NoOpLogger
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.io.encoding.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.io.IOException
import com.vplayed.shared.session.testSessions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Ported from AndroidJP's `GeoRestrictionRepositoryTest`, through the real clients on a MockEngine. */
class GeoRepositoryTest {

    private var ipAnswer: Pair<Int, String>? = 200 to """{"ip":"$TEST_IP"}"""
    private var geoAnswer: Pair<Int, String>? = 200 to geoBody(USD_TOKEN)
    private var gate: CompletableDeferred<Unit>? = null
    private var running = 0
    private var maxConcurrentRequests = 0

    private val recorder = RecordingEngine { request ->
        running++
        maxConcurrentRequests = maxOf(maxConcurrentRequests, running)
        try {
            gate?.await()
            val answer = if (request.url.host == "api.ipify.org") ipAnswer else geoAnswer
            val (status, body) = answer ?: throw IOException("offline")
            respond(body, HttpStatusCode.fromValue(status), headersOf(HttpHeaders.ContentType, "application/json"))
        } finally {
            running--
        }
    }

    private val graph = SharedGraph(
        testConfig(),
        PlatformDependencies(
            deviceInfo = FakeDeviceInfo(),
            logger = NoOpLogger,
            engineFactory = { recorder.engine },
            cryptoPrimitives = platformCryptoPrimitives(),
        ),
        testSessions(),
    )
    private val repository = graph.geoRepository
    private val store get() = graph.geoStore

    private val geoRequests get() = recorder.requests.filter { it.url.host != "api.ipify.org" }

    @Test
    fun givenIpAndToken_whenRefreshing_thenStoreHoldsIpTokenCurrencyAndLocation() = runTest {
        val outcome = repository.refresh()

        assertEquals(GeoRefreshOutcome.SUCCESS, outcome)
        assertEquals("/mediaview/api/v1/common/geo-data/$TEST_IP", geoRequests.single().url.encodedPath)
        assertEquals(GeoState(TEST_IP, USD_TOKEN, "USD", GeoInfo(countryCode = "US", currency = "USD")), store.current)
    }

    @Test
    fun givenRequests_whenRefreshing_thenTheIpLookupGetsNoAppHeadersAndGeoDataNoOldToken() = runTest {
        store.updateGeo("old-token", null)
        repository.refresh()

        val lookup = recorder.requests.first()
        assertEquals("https://api.ipify.org/?format=json", lookup.url.toString())
        assertNull(lookup.headers["device_id"])
        assertNull(lookup.headers["x-bearer-token"])
        assertNull(geoRequests.single().headers["x-bearer-token"], "The geo request must not carry the token it replaces")
    }

    @Test
    fun givenIpRequestFails_whenRefreshing_thenTransientFailureAndStateUnchanged() = runTest {
        ipAnswer = null

        assertEquals(GeoRefreshOutcome.TRANSIENT_FAILURE, repository.refresh())
        assertEquals(GeoState(), store.current)
        assertEquals(emptyList(), geoRequests)
    }

    @Test
    fun givenIpServiceError_whenRefreshing_thenTransientFailure() = runTest {
        ipAnswer = 503 to "<html>busy</html>"
        assertEquals(GeoRefreshOutcome.TRANSIENT_FAILURE, repository.refresh())
    }

    @Test
    fun givenInvalidIp_whenRefreshing_thenInvalidResponseAndNoGeoRequest() = runTest {
        ipAnswer = 200 to """{"ip":"not an ip"}"""
        assertEquals(GeoRefreshOutcome.INVALID_RESPONSE, repository.refresh())
        ipAnswer = 200 to """{"ip":null}"""
        assertEquals(GeoRefreshOutcome.INVALID_RESPONSE, repository.refresh())
        ipAnswer = 200 to """["$TEST_IP"]"""
        assertEquals(GeoRefreshOutcome.INVALID_RESPONSE, repository.refresh())
        assertEquals(emptyList(), geoRequests)
        assertNull(store.current.ip)
    }

    @Test
    fun givenPreviousValidState_whenGeoRequestFails_thenTokenAndCurrencyPreserved() = runTest {
        repository.refresh()
        geoAnswer = 503 to "{}"

        assertEquals(GeoRefreshOutcome.TRANSIENT_FAILURE, repository.refresh())
        assertEquals(USD_TOKEN, store.current.geoToken)
        assertEquals("USD", store.current.currency)
    }

    @Test
    fun givenPreviousValidState_whenTokenMissingOrBlank_thenPreviousStatePreserved() = runTest {
        repository.refresh()

        geoAnswer = 200 to """{"error":false,"response":{"geo_token":null}}"""
        assertEquals(GeoRefreshOutcome.INVALID_RESPONSE, repository.refresh())
        geoAnswer = 200 to geoBody("  ")
        assertEquals(GeoRefreshOutcome.INVALID_RESPONSE, repository.refresh())

        assertEquals(USD_TOKEN, store.current.geoToken)
        assertEquals("USD", store.current.currency)
    }

    @Test
    fun givenClientErrorOrRefusedEnvelope_whenRefreshing_thenInvalidResponseNotRetried() = runTest {
        geoAnswer = 404 to "{}"
        assertEquals(GeoRefreshOutcome.INVALID_RESPONSE, repository.refresh())
        geoAnswer = 200 to """{"statusCode":422,"message":"Bad IP"}"""
        assertEquals(GeoRefreshOutcome.INVALID_RESPONSE, repository.refresh())
        geoAnswer = 429 to "{}"
        assertEquals(GeoRefreshOutcome.TRANSIENT_FAILURE, repository.refresh())
    }

    @Test
    fun givenUndecodableToken_whenRefreshing_thenTokenSavedAndCurrencyStaysDefault() = runTest {
        geoAnswer = 200 to geoBody("%%%not-base64%%%")

        assertEquals(GeoRefreshOutcome.SUCCESS, repository.refresh())

        assertEquals("%%%not-base64%%%", store.current.geoToken)
        assertEquals(GeoState.DEFAULT_CURRENCY, store.current.currency)
        assertNull(store.current.info)
    }

    @Test
    fun givenConcurrentRefreshes_whenRunning_thenRequestsNeverOverlap() = runTest {
        gate = CompletableDeferred()

        val calls = List(3) { async { repository.refresh() } }
        yield()
        gate!!.complete(Unit)
        val outcomes = calls.awaitAll()

        assertEquals(List(3) { GeoRefreshOutcome.SUCCESS }, outcomes)
        assertEquals(1, maxConcurrentRequests)
    }

    @Test
    fun givenStateWithToken_whenPrinted_thenNeitherTokenNorIpAppear() = runTest {
        repository.refresh()
        val text = store.current.toString()
        assertEquals(false, text.contains(USD_TOKEN) || text.contains(TEST_IP))
    }

    private companion object {
        // Documentation range address (RFC 5737); no real user IPs in tests.
        const val TEST_IP = "203.0.113.10"
        val USD_TOKEN = Base64.encode("""{"currency":"USD","countryCode":"US"}""".encodeToByteArray())

        fun geoBody(token: String) = """{"error":false,"response":{"geo_token":"$token"}}"""
    }
}

/** Ported from AndroidJP's `GeoTokenDecoderTest`, plus VPlayed's location fields. */
class GeoTokenDecoderTest {

    private fun token(json: String) = Base64.encode(json.encodeToByteArray())

    @Test
    fun givenValidToken_whenDecoding_thenReturnsLocation() {
        val info = GeoTokenDecoder.decode(token("""{"currency":"USD","country":"United States","countryCode":"US","city":"Austin","timezone":"America/Chicago","regionName":"Texas","region":"TX"}"""))
        assertEquals(GeoInfo("United States", "US", "USD", "TX", "Texas", "Austin", "America/Chicago"), info)
        assertEquals("USD", GeoTokenDecoder.decodeCurrency(token("""{"currency":"USD","country":"US"}""")))
    }

    @Test
    fun givenTokenWithoutPaddingOrWithLineBreaks_whenDecoding_thenStillReadsCurrency() {
        val padded = token("""{"currency":"EUR"}""")
        assertEquals("EUR", GeoTokenDecoder.decodeCurrency(padded.trimEnd('=')))
        assertEquals("EUR", GeoTokenDecoder.decodeCurrency(padded.chunked(8).joinToString("\n")))
    }

    @Test
    fun givenInvalidBase64_whenDecoding_thenReturnsNullWithoutCrashing() {
        assertNull(GeoTokenDecoder.decode("%%%not-base64%%%"))
    }

    @Test
    fun givenMalformedJson_whenDecoding_thenReturnsNullWithoutCrashing() {
        assertNull(GeoTokenDecoder.decode(token("""{"currency":""")))
        assertNull(GeoTokenDecoder.decode(token("plain text")))
        assertNull(GeoTokenDecoder.decode(token("""["USD"]""")))
    }

    @Test
    fun givenMissingOrBlankCurrency_whenDecoding_thenCurrencyIsNull() {
        assertNull(GeoTokenDecoder.decodeCurrency(token("""{"country":"IN"}""")))
        assertNull(GeoTokenDecoder.decodeCurrency(token("""{"currency":"  "}""")))
        assertNull(GeoTokenDecoder.decodeCurrency(token("""{"currency":null}""")))
    }

    @Test
    fun givenBlankToken_whenDecoding_thenReturnsNull() {
        assertNull(GeoTokenDecoder.decode(""))
        assertNull(GeoTokenDecoder.decode("  \n"))
    }
}

class GeoStoreTest {
    @Test
    fun givenBlankValues_whenUpdating_thenTheyAreIgnored() {
        val store = GeoStore()
        store.updateIp(" ")
        store.updateGeo("", GeoInfo(currency = "USD"))
        assertEquals(GeoState(), store.current)
    }

    @Test
    fun givenTokenWithoutCurrency_whenUpdating_thenCurrencyIsKeptAndInfoFollowsTheToken() {
        val store = GeoStore()
        store.updateGeo("t1", GeoInfo(currency = "USD", countryCode = "US"))
        store.updateGeo("t2", null)
        assertEquals(GeoState(ip = null, geoToken = "t2", currency = "USD", info = null), store.current)
        assertEquals("t2", store.geoToken)
    }
}
